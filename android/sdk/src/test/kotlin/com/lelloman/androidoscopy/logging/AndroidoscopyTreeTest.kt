package com.lelloman.androidoscopy.logging

import android.util.Log
import com.lelloman.androidoscopy.protocol.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Method

class AndroidoscopyTreeTest {

    private val tree = AndroidoscopyTree()

    @Test
    fun `VERBOSE priority maps to VERBOSE level`() {
        val level = invokePrivatePriorityToLogLevel(Log.VERBOSE)
        assertEquals(LogLevel.VERBOSE, level)
    }

    @Test
    fun `DEBUG priority maps to DEBUG level`() {
        val level = invokePrivatePriorityToLogLevel(Log.DEBUG)
        assertEquals(LogLevel.DEBUG, level)
    }

    @Test
    fun `INFO priority maps to INFO level`() {
        val level = invokePrivatePriorityToLogLevel(Log.INFO)
        assertEquals(LogLevel.INFO, level)
    }

    @Test
    fun `WARN priority maps to WARN level`() {
        val level = invokePrivatePriorityToLogLevel(Log.WARN)
        assertEquals(LogLevel.WARN, level)
    }

    @Test
    fun `ERROR priority maps to ERROR level`() {
        val level = invokePrivatePriorityToLogLevel(Log.ERROR)
        assertEquals(LogLevel.ERROR, level)
    }

    @Test
    fun `ASSERT priority maps to ERROR level`() {
        val level = invokePrivatePriorityToLogLevel(Log.ASSERT)
        assertEquals(LogLevel.ERROR, level)
    }

    @Test
    fun `unknown priority defaults to DEBUG level`() {
        val level = invokePrivatePriorityToLogLevel(999)
        assertEquals(LogLevel.DEBUG, level)
    }

    private fun invokePrivatePriorityToLogLevel(priority: Int): LogLevel {
        val method: Method = AndroidoscopyTree::class.java.getDeclaredMethod("priorityToLogLevel", Int::class.java)
        method.isAccessible = true
        return method.invoke(tree, priority) as LogLevel
    }
}
