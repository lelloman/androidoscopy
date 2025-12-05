package com.lelloman.androidoscopy.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class DataProviderManagerTest {

    private lateinit var testScope: TestScope
    private lateinit var manager: DataProviderManager
    private val receivedData = mutableListOf<Pair<String, Map<String, Any>>>()

    @Before
    fun setUp() {
        testScope = TestScope(UnconfinedTestDispatcher())
        receivedData.clear()
        manager = DataProviderManager(testScope) { key, data ->
            receivedData.add(key to data)
        }
    }

    @Test
    fun `register adds provider`() {
        val provider = TestDataProvider("test", 1.seconds)
        manager.register(provider)

        assertEquals(1, manager.getRegisteredProviders().size)
        assertEquals("test", manager.getRegisteredProviders()[0].key)
    }

    @Test
    fun `unregister removes provider`() {
        val provider = TestDataProvider("test", 1.seconds)
        manager.register(provider)
        manager.unregister(provider)

        assertEquals(0, manager.getRegisteredProviders().size)
    }

    @Test
    fun `start sets running state`() {
        manager.start()
        assertTrue(manager.isRunning())
    }

    @Test
    fun `stop clears running state`() {
        manager.start()
        manager.stop()
        assertFalse(manager.isRunning())
    }

    @Test
    fun `isRunning returns false initially`() {
        assertFalse(manager.isRunning())
    }

    @Test
    fun `multiple providers can be registered`() {
        val provider1 = TestDataProvider("test1", 1.seconds)
        val provider2 = TestDataProvider("test2", 2.seconds)
        manager.register(provider1)
        manager.register(provider2)

        assertEquals(2, manager.getRegisteredProviders().size)
    }

    @Test
    fun `unregister specific provider keeps others`() {
        val provider1 = TestDataProvider("test1", 1.seconds)
        val provider2 = TestDataProvider("test2", 2.seconds)
        manager.register(provider1)
        manager.register(provider2)
        manager.unregister(provider1)

        assertEquals(1, manager.getRegisteredProviders().size)
        assertEquals("test2", manager.getRegisteredProviders()[0].key)
    }

    @Test
    fun `start is idempotent`() {
        manager.start()
        manager.start()
        assertTrue(manager.isRunning())
    }

    @Test
    fun `stop when not running is safe`() {
        manager.stop()
        assertFalse(manager.isRunning())
    }
}

private class TestDataProvider(
    override val key: String,
    override val interval: kotlin.time.Duration
) : DataProvider {
    private var collectCount = 0

    override suspend fun collect(): Map<String, Any> {
        collectCount++
        return mapOf("count" to collectCount)
    }
}
