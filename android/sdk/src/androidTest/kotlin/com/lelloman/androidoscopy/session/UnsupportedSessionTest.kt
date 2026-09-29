package com.lelloman.androidoscopy.session

import android.app.Application
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.lelloman.androidoscopy.AndroidoscopyConfig
import org.junit.Assert.*
import org.junit.Test

@SdkSuppress(maxSdkVersion = 30)
class UnsupportedSessionTest {
    @Test fun initializationStaysInactiveAndExplicitStartFailsBeforeStartingProviders() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val config = AndroidoscopyConfig().apply {
            appName = "Unsupported session test"
            dataProvider { error("Unsupported sessions must not create providers") }
        }
        val runtime = SessionRuntime(app, config)
        runtime.initialize()
        assertFalse(runtime.isActive)
        assertEquals(UNSUPPORTED_SESSION_TLS, runtime.state.value.reason)
        assertThrows(IllegalStateException::class.java) { runtime.start() }
        assertFalse(runtime.state.value.active)
        assertNull(runtime.state.value.sessionId)
        assertNull(runtime.state.value.address)
    }
}
