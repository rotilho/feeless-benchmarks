package dev.feeless.benchmarks.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertTrue

class VirtualDispatcherTest {
    @Test
    fun `virtual dispatcher runs coroutine work on virtual threads`() =
        runBlocking {
            // Given / When
            val isVirtualThread =
                withContext(Dispatchers.VIRTUAL) {
                    Thread.currentThread().isVirtual
                }

            // Then
            assertTrue(isVirtualThread)
        }
}
