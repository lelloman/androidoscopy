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

    private var clickCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.statusText)
        val clickCountText = findViewById<TextView>(R.id.clickCountText)
        val clickButton = findViewById<Button>(R.id.clickButton)

        updateClickCount(clickCountText)

        clickButton.setOnClickListener {
            clickCount++
            updateClickCount(clickCountText)
            (application as? SampleApplication)?.incrementClickCount()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Androidoscopy.connectionState.collect { state ->
                    statusText.text = when (state) {
                        is ConnectionState.Disconnected -> getString(R.string.connection_status_disconnected)
                        is ConnectionState.Connecting -> getString(R.string.connection_status_connecting)
                        is ConnectionState.Connected -> getString(R.string.connection_status_connected)
                        is ConnectionState.Error -> getString(R.string.connection_status_error, state.message)
                    }
                }
            }
        }
    }

    private fun updateClickCount(textView: TextView) {
        textView.text = getString(R.string.click_count_format, clickCount)
    }
}