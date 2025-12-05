package com.lelloman.lelloman.androidoscopy

import android.app.Application
import android.util.Log
import com.lelloman.androidoscopy.ActionResult
import com.lelloman.androidoscopy.Androidoscopy
import com.lelloman.androidoscopy.dashboard.ButtonStyle
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
            appName = "Androidoscopy Sample"

            dashboard {
                memorySection()
                logsSection()

                section("Sample Metrics") {
                    row {
                        number("Click Count", "\$.metrics.click_count")
                        text("Last Action", "\$.metrics.last_action")
                    }
                }

                section("Actions") {
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

            onAction("reset_counter") {
                _clickCount.value = 0
                updateMetrics()
                ActionResult.success("Counter reset to 0")
            }

            onAction("say_hello") {
                ActionResult.success("Hello from Androidoscopy Sample!")
            }
        }

        Log.d(TAG, "Androidoscopy initialized, updating metrics...")

        updateMetrics()
    }

    companion object {
        private const val TAG = "SampleApp"
    }

    fun incrementClickCount() {
        _clickCount.value++
        updateMetrics()
    }

    private fun updateMetrics() {
        val count = _clickCount.value
        Androidoscopy.updateData {
            put("metrics", mapOf(
                "click_count" to count,
                "last_action" to if (count == 0) "None" else "Button clicked"
            ))
        }
    }
}
