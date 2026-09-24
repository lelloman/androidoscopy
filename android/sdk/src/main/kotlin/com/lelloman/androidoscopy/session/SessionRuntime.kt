package com.lelloman.androidoscopy.session

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import com.lelloman.androidoscopy.*
import com.lelloman.androidoscopy.data.DataProvider
import com.lelloman.androidoscopy.protocol.LogLevel
import com.lelloman.androidoscopy.tools.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLSocket
import kotlin.time.Duration

internal class SessionRuntime(val app: Application, val config: AndroidoscopyConfig) {
    val state = MutableStateFlow(SessionState())
    val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val dataFlow = MutableStateFlow<Map<String, Any>>(emptyMap())
    val logFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val dashboardSchema get() = config.dashboardSchema
    val appName get() = config.appName
    private val clock = SessionClock(SystemClock::elapsedRealtime)
    private val debug = app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    private val peers by lazy { PeerStore(app) }
    private val tools = ConcurrentHashMap(config.tools)
    private val providers = ConcurrentHashMap<String, DataProvider>()
    private val providerJobs = ConcurrentHashMap<String, Job>()
    private val calls = ConcurrentHashMap<String, Job>()
    private val lock = Any()
    private var revision = 0L
    @Volatile private var generation: String? = null
    @Volatile private var scope: CoroutineScope? = null
    @Volatile private var transport: LanSocket? = null
    @Volatile private var pendingSocket: SSLSocket? = null
    @Volatile private var connection: FramedSocket? = null
    @Volatile private var answer: CompletableDeferred<PairingDecision>? = null
    @Volatile private var credential: ByteArray? = null
    @Volatile private var peer: String? = null
    @Volatile private var foreground = false
    private val pairingAttemptGate = PairingAttemptGate()
    val isActive get() = clock.active && !clock.isExpired()

    fun initialize() {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) { foreground = true }
            override fun onActivityPaused(activity: Activity) { foreground = false }
            override fun onActivityCreated(activity: Activity, saved: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, saved: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
        if (debug && config.sessionMode == SessionMode.AUTO) start()
    }

    fun start(timeout: Duration? = null) = synchronized(lock) {
        if (isActive) return@synchronized
        if (generation != null) stop("Session expired")
        check(debug || foreground) { "Start diagnostics from a foreground Activity" }
        if (Build.VERSION.SDK_INT >= 37 && app.applicationInfo.targetSdkVersion >= 37)
            check(app.checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") == 0) { "Local network permission required" }
        clock.start(if (debug) null else (timeout ?: config.releaseIdleTimeout).inWholeMilliseconds)
        val id = UUID.randomUUID().toString()
        generation = id
        val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = sessionScope
        state.value = SessionState(active = true, sessionId = id, remainingMs = clock.remainingMs())
        if (!debug) try {
            val intent = Intent(app, DiagnosticService::class.java)
            if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(intent) else app.startService(intent)
        } catch (e: Exception) { stop("Unable to start service: ${e.message}"); throw e }
        sessionScope.launch {
            while (generation == id) {
                delay(500)
                if (clock.isExpired()) { stop("Session expired"); break }
                synchronized(lock) { if (generation == id) state.value = state.value.copy(remainingMs = clock.remainingMs()) }
            }
        }
        sessionScope.launch {
            try {
                providers.values.forEach { collectProvider(it, id) }
                config.providerFactories.forEach { registerDataProvider(it()) }
                config.anrConfig?.let { registerDataProvider(com.lelloman.androidoscopy.anr.AnrDataProvider(it.thresholdMs, it.maxHistory)) }
                while (generation == id && isActive) {
                    val lan = LanSocket(app, peers.instanceId)
                    transport = lan
                    try {
                        val listener = lan.open()
                        if (generation != id || !isActive) { lan.close(); break }
                        synchronized(lock) { if (generation == id) state.value = state.value.copy(address = lan.address, reason = null) }
                        val monitor = launch {
                            while (generation == id) {
                                delay(2_000)
                                if (lan.localAddress() != listener.inetAddress) {
                                    pendingSocket?.close(); connection?.socket?.close(); lan.close(); break
                                }
                            }
                        }
                        try {
                            while (generation == id && isActive) {
                                val socket = listener.accept() as SSLSocket
                                if (pendingSocket != null || connection != null) { socket.close(); continue }
                                pendingSocket = socket
                                launch { serve(socket, id) }
                            }
                        } finally { monitor.cancel() }
                    } catch (e: Exception) {
                        synchronized(lock) { if (generation == id) state.value = state.value.copy(address = null, reason = e.message) }
                    } finally { lan.close() }
                    delay(2_000)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (generation == id) stop("Diagnostics failed: ${e.message}") }
        }
    }

    fun stop(reason: String) = synchronized(lock) {
        clock.stop(); generation = null
        answer?.cancel(); answer = null
        runCatching { pendingSocket?.close() }; pendingSocket = null
        runCatching { connection?.socket?.close() }; connection = null
        transport?.close(); transport = null
        scope?.cancel(); scope = null
        calls.clear(); providerJobs.clear()
        providers.values.forEach { runCatching { it.close() } }
        credential?.fill(0); credential = null; peer = null
        dataFlow.value = emptyMap(); logFlow.value = emptyList()
        state.value = SessionState(reason = reason)
        connectionState.value = ConnectionState.Disconnected
        app.stopService(Intent(app, DiagnosticService::class.java))
        DiagnosticService.dismiss(app)
    }

    fun activity() = synchronized(lock) { clock.activity() }
    fun rememberedPeers() = if (debug) peers.list() else emptyList()
    fun forgetPeer(id: String) = synchronized(lock) {
        peers.forget(id)
        if (peer == id) { credential?.fill(0); credential = null; peer = null; connection?.socket?.close() }
    }
    fun answerPairing(id: String, approved: Boolean) = synchronized(lock) {
        if (isActive && state.value.pairing?.id == id) answer?.complete(if (approved) PairingDecision.MANUAL else PairingDecision.REJECTED)
    }
    fun setAcceptAll(enabled: Boolean) = synchronized(lock) {
        check(isActive) { "Start a diagnostic session first" }
        state.value = state.value.withAcceptAll(enabled, foreground)
        clock.activity()
        if (enabled) answer?.complete(PairingDecision.AUTOMATIC)
    }
    fun registerTool(tool: Tool) { tools[tool.name] = tool; synchronized(lock) { revision++ }; scope?.launch { publishManifest() } }
    fun unregisterTool(name: String) { tools.remove(name); synchronized(lock) { revision++ }; scope?.launch { publishManifest() } }
    private fun publishManifest() = send(buildJsonObject {
        put("type", "TOOLS"); put("session", generation); put("revision", revision)
        put("tools", JsonArray(tools.values.sortedBy { it.name }.map { it.manifest() }))
    })
    fun registerDataProvider(provider: DataProvider) {
        providers.put(provider.key, provider)?.takeIf { it !== provider }?.close()
        generation?.let { collectProvider(provider, it) }
    }
    fun unregisterDataProvider(provider: DataProvider) {
        if (providers.remove(provider.key, provider)) { providerJobs.remove(provider.key)?.cancel(); provider.close() }
    }
    private fun collectProvider(provider: DataProvider, id: String) {
        providerJobs.remove(provider.key)?.cancel()
        providerJobs[provider.key] = scope?.launch {
            while (generation == id && isActive) {
                try {
                    val result = provider.collect()
                    synchronized(lock) { if (generation == id && isActive) dataFlow.value = dataFlow.value + (provider.key to result) }
                    send(buildJsonObject { put("type", "DATA"); put("session", id); put("data", dataFlow.value.toJson()) })
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { /* A failed collector must not stop unrelated tools. */ }
                delay(provider.interval.inWholeMilliseconds.coerceAtLeast(100))
            }
        } ?: return
    }
    fun updateData(block: MutableMap<String, Any>.() -> Unit) {
        if (!isActive) return
        val id = generation
        val data = dataFlow.value.toMutableMap().apply(block)
        synchronized(lock) { if (generation == id && isActive) dataFlow.value = data }
        scope?.launch { send(buildJsonObject { put("type", "DATA"); put("session", id); put("data", data.toJson()) }) }
    }
    fun log(level: LogLevel, tag: String?, message: String, throwable: Throwable?) = synchronized(lock) {
        if (!isActive || !config.enableLogging) return@synchronized
        val id = generation
        val entry = LogEntry(java.time.Instant.now().toString(), level, tag, message.take(16_000), throwable?.stackTraceToString()?.take(32_000))
        logFlow.value = (logFlow.value + entry).takeLast(1000)
        scope?.launch { send(buildJsonObject {
            put("type", "LOG"); put("session", id)
            put("log", buildJsonObject {
                put("timestamp", entry.timestamp); put("level", entry.level.name); put("tag", entry.tag)
                put("message", entry.message); put("throwable", entry.throwable)
            })
        }) }
    }
    private fun send(value: JsonObject) {
        val wire = connection ?: return
        if (!isActive || value["session"]?.jsonPrimitive?.content != generation) return
        // Large snapshots must not tear down an otherwise usable tool connection.
        if (value.toString().toByteArray().size > MAX_FRAME) return
        try { wire.write(value) } catch (_: Exception) { runCatching { wire.socket.close() } }
    }

    private suspend fun serve(socket: SSLSocket, id: String) {
        try {
            socket.soTimeout = 10_000; socket.startHandshake()
            val wire = FramedSocket(socket)
            val exporter = wire.exporter()
            wire.write(buildJsonObject { put("type", "HELLO"); put("version", 2); put("session", id); put("device", peers.instanceId) })
            val hello = wire.read()
            val remote = hello.getValue("peer").jsonPrimitive.content
            require(remote.matches(Regex("[a-zA-Z0-9-]{1,64}")))
            var secret = if (remote == peer) credential else if (debug) peers.get(remote) else null
            var remember = false
            if (hello["type"]?.jsonPrimitive?.content == "RESUME") {
                require(secret != null && PairingCrypto.equal(PairingCrypto.proof(secret, exporter, id),
                    PairingCrypto.unhex(hello.getValue("proof").jsonPrimitive.content))) { "INVALID_CREDENTIAL" }
                remember = debug && peers.get(remote)?.let { PairingCrypto.equal(it, requireNotNull(secret)) } == true
            } else {
                require(hello["type"]?.jsonPrimitive?.content == "PAIR")
                val now = SystemClock.elapsedRealtime()
                require(pairingAttemptGate.admit(now)) { "PAIRING_RATE_LIMITED" }
                val commitment = PairingCrypto.unhex(hello.getValue("commitment").jsonPrimitive.content)
                val nonce = PairingCrypto.random()
                wire.write(buildJsonObject { put("type", "CHALLENGE"); put("nonce", PairingCrypto.hex(nonce)) })
                val clientNonce = PairingCrypto.unhex(wire.read().getValue("nonce").jsonPrimitive.content)
                require(PairingCrypto.equal(commitment, PairingCrypto.commitment(exporter, clientNonce)))
                val request = PairingRequest(UUID.randomUUID().toString(), PairingCrypto.code(exporter, clientNonce, nonce), socket.inetAddress.hostAddress ?: "PC")
                val decision = CompletableDeferred<PairingDecision>()
                val automatic = synchronized(lock) {
                    check(generation == id && isActive)
                    answer = decision
                    val automatic = state.value.acceptAll
                    state.value = state.value.copy(pairing = if (automatic) null else request, reason = null)
                    if (automatic) decision.complete(PairingDecision.AUTOMATIC)
                    automatic
                }
                if (!automatic) DiagnosticService.showPairing(app)
                val approval = withTimeout(60_000) { decision.await() }
                require(approval != PairingDecision.REJECTED) { "PAIRING_REJECTED" }
                synchronized(lock) { check(generation == id && clock.activity()); state.value = state.value.copy(pairing = null) }
                secret = PairingCrypto.random()
                // Automatically accepted PCs must never become permanently trusted.
                remember = shouldRememberPeer(debug, approval)
                if (remember) peers.remember(remote, secret) else if (debug) peers.forget(remote)
            }
            synchronized(lock) {
                check(generation == id && isActive)
                if (credential !== secret) credential?.fill(0)
                peer = remote; credential = secret; connection = wire; pendingSocket = null
                state.value = state.value.withConnectedPeer(remote)
                connectionState.value = ConnectionState.Connected(id)
            }
            socket.soTimeout = 0
            wire.write(buildJsonObject {
                put("type", "AUTHORIZED"); put("session", id); put("credential", PairingCrypto.hex(requireNotNull(secret)))
                put("remember", remember); put("app_name", config.appName); put("package_name", app.packageName)
                put("remainingMs", clock.remainingMs())
                put("dashboard", config.dashboardSchema ?: JsonObject(emptyMap()))
            })
            publishManifest()
            send(buildJsonObject { put("type", "DATA"); put("session", id); put("data", dataFlow.value.toJson()) })
            while (generation == id && isActive) {
                val message = wire.read()
                require(message["session"]?.jsonPrimitive?.content == id) { "STALE_SESSION" }
                when (message["type"]?.jsonPrimitive?.content) {
                    "CALL", "ACTION" -> dispatch(message, wire, id)
                    "CANCEL" -> calls.remove(message["id"]?.jsonPrimitive?.content)?.cancel()
                    "PING" -> wire.write(buildJsonObject { put("type", "PONG"); put("session", id); put("remainingMs", clock.remainingMs()) })
                    "ACTIVITY" -> activity()
                }
            }
        } catch (e: TimeoutCancellationException) {
            synchronized(lock) { if (generation == id && isActive) state.value = state.value.copy(reason = connectionEndedReason(e)) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            synchronized(lock) { if (generation == id && isActive) state.value = state.value.copy(reason = connectionEndedReason(e)) }
        }
        finally {
            runCatching { socket.close() }
            synchronized(lock) { if (generation == id) {
                connection = null; pendingSocket = null; answer?.cancel(); answer = null
                calls.values.forEach { it.cancel() }; calls.clear()
                state.value = state.value.copy(peer = null, pairing = null)
                connectionState.value = ConnectionState.Disconnected
            } }
        }
    }

    private fun dispatch(message: JsonObject, wire: FramedSocket, session: String) {
        val id = message["id"]?.jsonPrimitive?.content ?: return
        require(id.length <= 128 && !calls.containsKey(id) && calls.size < 16)
        val name = message["name"]?.jsonPrimitive?.content ?: return
        val args = message["arguments"] as? JsonObject
        val tool = tools[name]
        val action = if (message["type"]?.jsonPrimitive?.content == "ACTION") config.actionHandlers[name] else null
        val job = scope?.launch(start = CoroutineStart.LAZY) {
            val result = try {
                require(tool != null || action != null) { "UNKNOWN_TOOL" }
                require(args != null) { "INVALID_ARGUMENTS: expected object" }
                tool?.validateInput(args)
                check(generation == session && clock.activity()) { "SESSION_EXPIRED" }
                withTimeout(tool?.timeout?.inWholeMilliseconds ?: 60_000) {
                    if (tool != null) tool.handler(args).also(tool::validateOutput)
                    else {
                        @Suppress("UNCHECKED_CAST")
                        val response = action!!(args.toValue() as Map<String, Any>)
                        ToolResult.json(buildJsonObject {
                            put("success", response.success); put("message", response.message); put("data", response.data.toJson())
                        }).copy(isError = !response.success)
                    }
                }
            } catch (e: TimeoutCancellationException) { ToolResult.text("TOOL_TIMEOUT", true) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { ToolResult.text(e.message ?: "TOOL_FAILED", true) }
            if (generation == session && isActive && connection === wire) {
                var response = buildJsonObject { put("type", "RESULT"); put("session", session); put("id", id); put("result", result.toJson()) }
                if (response.toString().toByteArray().size > MAX_FRAME) response = buildJsonObject {
                    put("type", "RESULT"); put("session", session); put("id", id); put("result", ToolResult.text("RESULT_TOO_LARGE", true).toJson())
                }
                send(response)
            }
        } ?: return
        calls[id] = job; job.invokeOnCompletion { calls.remove(id, job) }; job.start()
    }

    suspend fun invokeAction(name: String, args: Map<String, Any>): ActionResult {
        if (!isActive || !activity()) return ActionResult.failure("SESSION_INACTIVE")
        val job = scope?.async {
            withTimeout(60_000) {
                config.actionHandlers[name]?.invoke(args) ?: tools[name]?.let {
                    val json = args.toJson().jsonObject; it.validateInput(json)
                    val result = it.handler(json).also(it::validateOutput)
                    ActionResult(!result.isError, result.toJson().toString())
                } ?: ActionResult.failure("UNKNOWN_TOOL")
            }
        } ?: return ActionResult.failure("SESSION_INACTIVE")
        return try { job.await() } catch (e: CancellationException) { job.cancel(); throw e }
        catch (e: Exception) { ActionResult.failure(e.message ?: "Action failed") }
    }
}
