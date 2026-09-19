package com.lelloman.androidoscopy.ui

import android.content.Intent
import android.os.Parcel
import androidx.compose.material3.darkColorScheme
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionPaletteIntentTest {
    @Test fun paletteSurvivesIntentParcelWithoutLeakingIntoDefaultLaunch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val palette = SessionPalette.fromColorScheme(darkColorScheme())
        val intent = SessionActivity.createIntent(context, palette)
        val parcel = Parcel.obtain()
        try {
            intent.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            val restored = Intent.CREATOR.createFromParcel(parcel)
            val key = restored.extras!!.keySet().single()
            assertEquals(palette, SessionPalette.fromArgbArray(restored.getIntArrayExtra(key)))
            assertEquals(SessionActivity::class.java.name, restored.component!!.className)
            assertNull(SessionActivity.createIntent(context).extras)
        } finally {
            parcel.recycle()
        }
    }
}
