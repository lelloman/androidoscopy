package com.lelloman.androidoscopy.anr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnrDataClassesTest {

    @Test
    fun `StackTraceElement format with file and line`() {
        val element = StackTraceElement(
            className = "com.example.MyClass",
            methodName = "myMethod",
            fileName = "MyClass.kt",
            lineNumber = 42
        )
        assertEquals("com.example.MyClass.myMethod(MyClass.kt:42)", element.format())
    }

    @Test
    fun `StackTraceElement format with file only`() {
        val element = StackTraceElement(
            className = "com.example.MyClass",
            methodName = "myMethod",
            fileName = "MyClass.kt",
            lineNumber = -1
        )
        assertEquals("com.example.MyClass.myMethod(MyClass.kt)", element.format())
    }

    @Test
    fun `StackTraceElement format with no file`() {
        val element = StackTraceElement(
            className = "com.example.MyClass",
            methodName = "myMethod",
            fileName = null,
            lineNumber = -1
        )
        assertEquals("com.example.MyClass.myMethod(Unknown Source)", element.format())
    }

    @Test
    fun `AnrInfo contains correct fields`() {
        val anrInfo = AnrInfo(
            timestamp = 1234567890L,
            durationMs = 5000L,
            mainThreadStackTrace = listOf(
                StackTraceElement("Main", "run", "Main.kt", 10)
            ),
            allThreads = emptyList()
        )
        assertEquals(1234567890L, anrInfo.timestamp)
        assertEquals(5000L, anrInfo.durationMs)
        assertEquals(1, anrInfo.mainThreadStackTrace.size)
    }

    @Test
    fun `ThreadInfo isMain flag`() {
        val mainThread = ThreadInfo(
            id = 1L,
            name = "main",
            state = "RUNNABLE",
            isMain = true,
            stackTrace = emptyList()
        )
        val workerThread = ThreadInfo(
            id = 2L,
            name = "worker",
            state = "WAITING",
            isMain = false,
            stackTrace = emptyList()
        )
        assertTrue(mainThread.isMain)
        assertFalse(workerThread.isMain)
    }

    @Test
    fun `AnrWatchdog companion object defaults`() {
        assertEquals(4000L, AnrWatchdog.DEFAULT_THRESHOLD_MS)
        assertEquals(1000L, AnrWatchdog.DEFAULT_CHECK_INTERVAL_MS)
    }
}
