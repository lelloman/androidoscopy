package com.lelloman.lelloman.androidoscopy

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.lelloman.androidoscopy.ActionResult
import com.lelloman.androidoscopy.Androidoscopy
import com.lelloman.androidoscopy.BuiltInActions
import com.lelloman.androidoscopy.anr.AnrDataProvider
import com.lelloman.androidoscopy.buildinfo.BuildInfoDataProvider
import com.lelloman.androidoscopy.coil.CoilDataProvider
import com.lelloman.androidoscopy.dashboard.ButtonStyle
import com.lelloman.androidoscopy.data.BatteryDataProvider
import com.lelloman.androidoscopy.data.MemoryDataProvider
import com.lelloman.androidoscopy.data.NetworkDataProvider
import com.lelloman.androidoscopy.data.StorageDataProvider
import com.lelloman.androidoscopy.data.ThreadDataProvider
import com.lelloman.androidoscopy.leakcanary.LeakDataProvider
import com.lelloman.androidoscopy.okhttp.AndroidoscopyInterceptor
import com.lelloman.androidoscopy.permissions.PermissionsDataProvider
import com.lelloman.androidoscopy.prefs.SharedPreferencesDataProvider
import com.lelloman.androidoscopy.sqlite.SqliteDataProvider
import com.lelloman.androidoscopy.timber.AndroidoscopyTree
import com.lelloman.androidoscopy.workmanager.WorkManagerDataProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import leakcanary.LeakCanary
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

class SampleApplication : Application() {

    private val _clickCount = MutableStateFlow(0)
    val clickCount: StateFlow<Int> = _clickCount.asStateFlow()

    // OkHttp client with Androidoscopy interceptor
    lateinit var okHttpClient: OkHttpClient
        private set

    // Coil ImageLoader
    lateinit var imageLoader: ImageLoader
        private set

    // Data providers with action handlers
    private lateinit var sharedPreferencesDataProvider: SharedPreferencesDataProvider
    private lateinit var sqliteDataProvider: SqliteDataProvider
    private lateinit var workManagerDataProvider: WorkManagerDataProvider
    private lateinit var coilDataProvider: CoilDataProvider
    private lateinit var leakDataProvider: LeakDataProvider

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber first (before Androidoscopy so logs are captured)
        Timber.plant(Timber.DebugTree())

        Timber.d("Initializing demo application...")

        // Setup OkHttp with Androidoscopy interceptor
        setupOkHttp()

        // Setup Coil ImageLoader
        setupCoil()

        // Setup LeakCanary with Androidoscopy
        leakDataProvider = LeakDataProvider()
        setupLeakCanary()

        // Initialize SharedPreferences with demo data
        setupDemoPreferences()

        // Initialize SQLite database with demo data
        setupDemoDatabase()

        // Schedule some WorkManager jobs for demo
        setupDemoWorkManager()

        // Initialize data providers
        sharedPreferencesDataProvider = SharedPreferencesDataProvider(this)
        sqliteDataProvider = SqliteDataProvider(this)
        workManagerDataProvider = WorkManagerDataProvider(this)
        coilDataProvider = CoilDataProvider(imageLoader)

        // Initialize Androidoscopy
        Androidoscopy.init(this) {
            appName = "Androidoscopy Demo"

            dashboard {
                // System metrics
                memorySection(includeActions = true)
                batterySection()
                storageSection()
                threadSection()
                networkSection()

                // New features showcase
                anrSection()
                networkRequestsSection()
                sharedPreferencesSection()
                sqliteSection()
                permissionsSection()
                buildInfoSection()
                leaksSection()
                workManagerSection()
                coilSection()

                // Logs
                logsSection()

                // Custom demo section
                section("Demo Metrics") {
                    row {
                        number("Click Count", "\$.metrics.click_count")
                        text("Last Action", "\$.metrics.last_action")
                    }
                }

                // Custom actions
                section("Custom Actions") {
                    actions {
                        button(
                            label = "Reset Counter",
                            action = "reset_counter",
                            style = ButtonStyle.DANGER
                        )
                        button(
                            label = "Trigger ANR (5s)",
                            action = "trigger_anr",
                            style = ButtonStyle.DANGER
                        )
                        button(
                            label = "Create Memory Leak",
                            action = "create_leak",
                            style = ButtonStyle.DANGER
                        )
                    }
                }
            }

            // Built-in actions
            onAction(BuiltInActions.FORCE_GC, BuiltInActions.forceGc())
            onAction(BuiltInActions.CLEAR_CACHE, BuiltInActions.clearCache(this@SampleApplication))

            // Custom actions
            onAction("reset_counter") {
                _clickCount.value = 0
                updateMetrics("Counter reset")
                ActionResult.success("Counter reset to 0")
            }

            onAction("trigger_anr") {
                // This will trigger ANR detection
                Thread.sleep(5000)
                ActionResult.success("ANR triggered")
            }

            onAction("create_leak") {
                // Create a fake leak for demo (this won't actually leak but shows the UI)
                createDemoLeak()
                ActionResult.success("Fake leak created for demo")
            }

            // Register action handlers from data providers
            sharedPreferencesDataProvider.getActionHandlers().forEach { (action, handler) ->
                onAction(action, handler)
            }
            sqliteDataProvider.getActionHandlers().forEach { (action, handler) ->
                onAction(action, handler)
            }
            workManagerDataProvider.getActionHandlers().forEach { (action, handler) ->
                onAction(action, handler)
            }
            coilDataProvider.getActionHandlers().forEach { (action, handler) ->
                onAction(action, handler)
            }

            // OkHttp interceptor actions
            AndroidoscopyInterceptor.instance.dataProvider.let { provider ->
                onAction("network_clear") { _ ->
                    AndroidoscopyInterceptor.instance.clear()
                    ActionResult.success("Network history cleared")
                }
            }
        }

        // Register all data providers
        Androidoscopy.registerDataProvider(MemoryDataProvider(this))
        Androidoscopy.registerDataProvider(BatteryDataProvider(this))
        Androidoscopy.registerDataProvider(StorageDataProvider(this))
        Androidoscopy.registerDataProvider(ThreadDataProvider())
        Androidoscopy.registerDataProvider(NetworkDataProvider(this))
        Androidoscopy.registerDataProvider(AnrDataProvider())
        Androidoscopy.registerDataProvider(AndroidoscopyInterceptor.instance.dataProvider)
        Androidoscopy.registerDataProvider(sharedPreferencesDataProvider)
        Androidoscopy.registerDataProvider(sqliteDataProvider)
        Androidoscopy.registerDataProvider(PermissionsDataProvider(this))
        Androidoscopy.registerDataProvider(BuildInfoDataProvider(
            context = this,
            gitSha = "abc123def",
            buildType = "debug",
            flavor = "demo"
        ))
        Androidoscopy.registerDataProvider(leakDataProvider)
        Androidoscopy.registerDataProvider(workManagerDataProvider)
        Androidoscopy.registerDataProvider(coilDataProvider)

        Timber.plant(AndroidoscopyTree())
        Timber.i("Androidoscopy initialized with all data providers and integration modules")

        // Send initial metrics
        updateMetrics("App started")
    }

    private fun setupOkHttp() {
        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(AndroidoscopyInterceptor.instance)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        Timber.d("OkHttp client configured with Androidoscopy interceptor")
    }

    private fun setupCoil() {
        imageLoader = ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(this, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "image_cache").toOkioPath())
                    .maxSizePercent(0.02)
                    .build()
            }
            .crossfade(true)
            .build()

        Timber.d("Coil ImageLoader configured")
    }

    private fun setupLeakCanary() {
        // Configure LeakCanary to use our listener
        LeakCanary.config = LeakCanary.config.copy(
            eventListeners = LeakCanary.config.eventListeners + leakDataProvider.eventListener
        )

        Timber.d("LeakCanary configured with Androidoscopy listener")
    }

    private fun setupDemoPreferences() {
        // Create some demo SharedPreferences entries
        getSharedPreferences("demo_prefs", MODE_PRIVATE).edit().apply {
            putString("username", "demo_user")
            putInt("login_count", 42)
            putBoolean("dark_mode", true)
            putFloat("volume", 0.75f)
            putLong("last_login", System.currentTimeMillis())
            apply()
        }

        getSharedPreferences("settings", MODE_PRIVATE).edit().apply {
            putBoolean("notifications_enabled", true)
            putString("theme", "system")
            putInt("cache_size_mb", 100)
            apply()
        }

        Timber.d("Demo SharedPreferences created")
    }

    private fun setupDemoDatabase() {
        // Create a simple demo database
        val db = openOrCreateDatabase("demo.db", MODE_PRIVATE, null)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                email TEXT,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS products (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                price REAL,
                stock INTEGER DEFAULT 0
            )
        """)

        // Insert demo data if table is empty
        val cursor = db.rawQuery("SELECT COUNT(*) FROM users", null)
        cursor.moveToFirst()
        if (cursor.getInt(0) == 0) {
            db.execSQL("INSERT INTO users (name, email) VALUES ('Alice', 'alice@example.com')")
            db.execSQL("INSERT INTO users (name, email) VALUES ('Bob', 'bob@example.com')")
            db.execSQL("INSERT INTO users (name, email) VALUES ('Charlie', 'charlie@example.com')")

            db.execSQL("INSERT INTO products (name, price, stock) VALUES ('Widget', 9.99, 100)")
            db.execSQL("INSERT INTO products (name, price, stock) VALUES ('Gadget', 24.99, 50)")
            db.execSQL("INSERT INTO products (name, price, stock) VALUES ('Gizmo', 14.99, 75)")
        }
        cursor.close()
        db.close()

        Timber.d("Demo SQLite database created")
    }

    private fun setupDemoWorkManager() {
        val workManager = WorkManager.getInstance(this)

        // Schedule a periodic sync work
        val syncWorkRequest = PeriodicWorkRequestBuilder<DemoSyncWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag("sync")
            .addTag("periodic")
            .build()

        workManager.enqueueUniquePeriodicWork(
            "demo_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncWorkRequest
        )

        // Schedule a one-time cleanup work
        val cleanupWorkRequest = OneTimeWorkRequestBuilder<DemoCleanupWorker>()
            .addTag("cleanup")
            .addTag("one-time")
            .build()

        workManager.enqueue(cleanupWorkRequest)

        Timber.d("Demo WorkManager jobs scheduled")
    }

    @Suppress("unused")
    private fun createDemoLeak() {
        // This is just for demonstration - it shows what the leak UI looks like
        // In a real app, LeakCanary would detect actual leaks
        Timber.w("Demo leak simulation - in production, LeakCanary detects real leaks")
    }

    fun incrementClickCount() {
        _clickCount.value++
        updateMetrics("Button clicked")
        Timber.d("Click count incremented to ${_clickCount.value}")
    }

    private fun updateMetrics(lastAction: String) {
        val count = _clickCount.value
        Androidoscopy.updateData {
            put("metrics", mapOf(
                "click_count" to count,
                "last_action" to lastAction
            ))
        }
    }

    companion object {
        private const val TAG = "SampleApp"
    }
}

// Demo WorkManager workers
class DemoSyncWorker(context: android.content.Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        Timber.d("DemoSyncWorker running...")
        Thread.sleep(2000) // Simulate work
        Timber.d("DemoSyncWorker completed")
        return Result.success()
    }
}

class DemoCleanupWorker(context: android.content.Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        Timber.d("DemoCleanupWorker running...")
        Thread.sleep(1000) // Simulate work
        Timber.d("DemoCleanupWorker completed")
        return Result.success()
    }
}
