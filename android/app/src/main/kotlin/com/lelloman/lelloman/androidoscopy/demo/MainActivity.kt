package com.lelloman.lelloman.androidoscopy.demo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.lelloman.androidoscopy.Androidoscopy
import com.lelloman.lelloman.androidoscopy.R
import com.lelloman.androidoscopy.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val counter = AtomicInteger(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        observeConnectionState()
        startDemoDataUpdates()

        Timber.i("MainActivity created")
    }

    private fun observeConnectionState() {
        scope.launch {
            Androidoscopy.connectionState.collectLatest { state ->
                when (state) {
                    is ConnectionState.Connected -> {
                        Timber.i("Connected to Androidoscopy dashboard")
                    }
                    is ConnectionState.Connecting -> {
                        Timber.d("Connecting to Androidoscopy dashboard...")
                    }
                    is ConnectionState.Disconnected -> {
                        Timber.d("Disconnected from Androidoscopy dashboard")
                    }
                    is ConnectionState.Error -> {
                        Timber.e("Connection error: ${state.message}")
                    }
                }
            }
        }
    }

    private fun startDemoDataUpdates() {
        scope.launch {
            while (true) {
                val count = counter.incrementAndGet()
                Androidoscopy.updateData {
                    put("demo", mapOf(
                        "counter" to count,
                        "random_value" to (Math.random() * 100).toInt(),
                        "timestamp" to System.currentTimeMillis()
                    ))
                }

                if (count % 10 == 0) {
                    Timber.d("Demo counter reached $count")
                }

                kotlinx.coroutines.delay(1000)
            }
        }
    }
}
