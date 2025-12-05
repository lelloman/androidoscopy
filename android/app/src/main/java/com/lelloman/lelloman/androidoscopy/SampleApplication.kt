package com.lelloman.lelloman.androidoscopy

import android.app.Application
import android.util.Log
import com.lelloman.androidoscopy.ActionResult
import com.lelloman.androidoscopy.Androidoscopy
import com.lelloman.androidoscopy.dashboard.ButtonStyle

class SampleApplication : Application() {

    private var clickCount = 0

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "Initializing Androidoscopy...")

        Androidoscopy.init(this) {
            appName = "Androidoscopy Sample"
            autoConnect = true

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
                clickCount = 0
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
        clickCount++
        updateMetrics()
    }

    private fun updateMetrics() {
        Androidoscopy.updateData {
            put("metrics", mapOf(
                "click_count" to clickCount,
                "last_action" to if (clickCount == 0) "None" else "Button clicked"
            ))
        }
    }
}
