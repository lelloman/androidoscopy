package com.lelloman.lelloman.androidoscopy

import com.lelloman.androidoscopy.Androidoscopy
import com.lelloman.androidoscopy.leakcanary.LeakDataProvider
import leakcanary.LeakCanary

internal object DemoLeaks {
    fun install() {
        val provider = LeakDataProvider()
        LeakCanary.config = LeakCanary.config.copy(eventListeners = LeakCanary.config.eventListeners + provider.eventListener)
        Androidoscopy.registerDataProvider(provider)
    }
}
