package com.lelloman.lelloman.androidoscopy

import android.app.Application
import android.util.Log
import com.lelloman.androidoscopy.ActionResult
import com.lelloman.androidoscopy.Androidoscopy
import com.lelloman.androidoscopy.BuiltInActions
import com.lelloman.androidoscopy.dashboard.ButtonStyle
import com.lelloman.androidoscopy.data.BatteryDataProvider
import com.lelloman.androidoscopy.data.MemoryDataProvider
import com.lelloman.androidoscopy.data.NetworkDataProvider
import com.lelloman.androidoscopy.data.StorageDataProvider
import com.lelloman.androidoscopy.data.ThreadDataProvider
import com.lelloman.androidoscopy.protocol.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SampleApplication : Application() {

    private val _clickCount = MutableStateFlow(0)
    val clickCount: StateFlow<Int> = _clickCount.asStateFlow()

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "Initializing Androidoscopy...")

        Androidoscopy.init(this) {
            appName = "Androidoscopy Demo"

            dashboard {
                // Built-in sections for system metrics
                memorySection(includeActions = true)
                batterySection()
                storageSection()
                threadSection()
                networkSection()
                logsSection()

                // Custom section for demo metrics
                section("Demo Metrics") {
                    row {
                        number("Click Count", "\$.metrics.click_count")
                        text("Last Action", "\$.metrics.last_action")
                    }
                }

                // Custom actions section
                section("Custom Actions") {
                    actions {
                        button(
                            label = "Reset Counter",
                            action = "reset_counter",
                            style = ButtonStyle.DANGER
                        )
                        button(
                            label = "Say Hello",
                            action = "say_hello",
                            style = ButtonStyle.PRIMARY
                        )
                    }
                }
            }

            // Register built-in actions
            onAction(BuiltInActions.FORCE_GC, BuiltInActions.forceGc())
            onAction(BuiltInActions.CLEAR_CACHE, BuiltInActions.clearCache(this@SampleApplication))

            // Register custom actions
            onAction("reset_counter") {
                _clickCount.value = 0
                updateMetrics("Counter reset")
                ActionResult.success("Counter reset to 0")
            }

            onAction("say_hello") {
                ActionResult.success("Hello from Androidoscopy Demo!")
            }
        }

        // Register all data providers
        Androidoscopy.registerDataProvider(MemoryDataProvider(this))
        Androidoscopy.registerDataProvider(BatteryDataProvider(this))
        Androidoscopy.registerDataProvider(StorageDataProvider(this))
        Androidoscopy.registerDataProvider(ThreadDataProvider())
        Androidoscopy.registerDataProvider(NetworkDataProvider(this))

        Log.d(TAG, "Androidoscopy initialized with all data providers")

        // Send initial metrics
        updateMetrics("App started")

        // Log startup
        Androidoscopy.log(LogLevel.INFO, TAG, "Demo application started successfully")
    }

    fun incrementClickCount() {
        _clickCount.value++
        updateMetrics("Button clicked")
        Androidoscopy.log(LogLevel.DEBUG, TAG, "Click count incremented to ${_clickCount.value}")
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
