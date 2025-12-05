package com.lelloman.lelloman.androidoscopy

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lelloman.androidoscopy.Androidoscopy
import com.lelloman.androidoscopy.ConnectionState
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.statusText)
        val clickCountText = findViewById<TextView>(R.id.clickCountText)
        val clickButton = findViewById<Button>(R.id.clickButton)

        val app = application as? SampleApplication

        clickButton.setOnClickListener {
            app?.incrementClickCount()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe connection state
                launch {
                    Androidoscopy.connectionState.collect { state ->
                        statusText.text = when (state) {
                            is ConnectionState.Disconnected -> getString(R.string.connection_status_disconnected)
                            is ConnectionState.Connecting -> getString(R.string.connection_status_connecting)
                            is ConnectionState.Connected -> getString(R.string.connection_status_connected)
                            is ConnectionState.Error -> getString(R.string.connection_status_error, state.message)
                        }
                    }
                }

                // Observe click count
                launch {
                    app?.clickCount?.collect { count ->
                        clickCountText.text = getString(R.string.click_count_format, count)
                    }
                }
            }
        }
    }
}