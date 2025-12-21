package com.lelloman.androidoscopy.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/**
 * Activity that displays the Androidoscopy dashboard.
 *
 * This activity can be launched independently to show the same dashboard
 * content that appears in the web interface, but directly on the device.
 *
 * Usage:
 * ```kotlin
 * // From anywhere in your app:
 * DashboardActivity.launch(context)
 *
 * // Or with an intent:
 * startActivity(Intent(this, DashboardActivity::class.java))
 *
 * // Or via the action:
 * startActivity(Intent("com.lelloman.androidoscopy.DASHBOARD"))
 * ```
 *
 * Note: Androidoscopy.init() must be called before launching this activity.
 */
class DashboardActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            DashboardScreen()
        }
    }

    companion object {
        /**
         * Launches the dashboard activity.
         *
         * @param context The context to use for launching the activity.
         */
        fun launch(context: Context) {
            val intent = Intent(context, DashboardActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }

        /**
         * Creates an intent for launching the dashboard activity.
         * Useful for integration with navigation components or custom launchers.
         *
         * @param context The context to use for creating the intent.
         */
        fun createIntent(context: Context): Intent {
            return Intent(context, DashboardActivity::class.java)
        }
    }
}
