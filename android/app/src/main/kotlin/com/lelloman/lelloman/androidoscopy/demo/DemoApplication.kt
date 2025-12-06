package com.lelloman.lelloman.androidoscopy.demo

import android.app.Application
import com.lelloman.androidoscopy.Androidoscopy
import com.lelloman.androidoscopy.BuiltInActions
import com.lelloman.androidoscopy.data.BatteryDataProvider
import com.lelloman.androidoscopy.data.MemoryDataProvider
import com.lelloman.androidoscopy.data.StorageDataProvider
import com.lelloman.androidoscopy.data.ThreadDataProvider
import com.lelloman.androidoscopy.logging.AndroidoscopyTree
import timber.log.Timber

class DemoApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        Timber.plant(Timber.DebugTree())
        Timber.plant(AndroidoscopyTree())

        Androidoscopy.init(this) {
            appName = "Androidoscopy Demo"

            dashboard {
                memorySection(includeActions = true)
                batterySection()
                storageSection()
                threadSection()
                logsSection()
            }

            onAction(BuiltInActions.FORCE_GC, BuiltInActions.forceGc())
            onAction(BuiltInActions.CLEAR_CACHE, BuiltInActions.clearCache(this@DemoApplication))
        }

        Androidoscopy.registerDataProvider(MemoryDataProvider(this))
        Androidoscopy.registerDataProvider(BatteryDataProvider(this))
        Androidoscopy.registerDataProvider(StorageDataProvider(this))
        Androidoscopy.registerDataProvider(ThreadDataProvider())

        Timber.d("DemoApplication", "Demo application started")
    }
}
