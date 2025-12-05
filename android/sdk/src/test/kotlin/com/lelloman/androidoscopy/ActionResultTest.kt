package com.lelloman.androidoscopy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionResultTest {

    @Test
    fun `success creates result with success true`() {
        val result = ActionResult.success()

        assertTrue(result.success)
        assertNull(result.message)
        assertNull(result.data)
    }

    @Test
    fun `success with message includes message`() {
        val result = ActionResult.success("Operation completed")

        assertTrue(result.success)
        assertEquals("Operation completed", result.message)
        assertNull(result.data)
    }

    @Test
    fun `success with data includes data`() {
        val data = mapOf("count" to 10, "name" to "test")
        val result = ActionResult.success(data = data)

        assertTrue(result.success)
        assertEquals(data, result.data)
    }

    @Test
    fun `success with message and data includes both`() {
        val data = mapOf("id" to "123")
        val result = ActionResult.success("Created", data)

        assertTrue(result.success)
        assertEquals("Created", result.message)
        assertEquals(data, result.data)
    }

    @Test
    fun `failure creates result with success false`() {
        val result = ActionResult.failure("Something went wrong")

        assertFalse(result.success)
        assertEquals("Something went wrong", result.message)
        assertNull(result.data)
    }

    @Test
    fun `constructor creates correct result`() {
        val result = ActionResult(
            success = true,
            message = "Test",
            data = mapOf("key" to "value")
        )

        assertTrue(result.success)
        assertEquals("Test", result.message)
        assertEquals(mapOf("key" to "value"), result.data)
    }

    @Test
    fun `failure always has null data`() {
        val result = ActionResult.failure("Error")
        assertNull(result.data)
    }
}
