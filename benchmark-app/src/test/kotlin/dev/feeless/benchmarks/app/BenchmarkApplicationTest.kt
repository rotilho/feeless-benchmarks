package dev.feeless.benchmarks.app

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BenchmarkApplicationTest {
    @Test
    fun `help returns success without starting a benchmark`() =
        runTest {
            // Given
            val output = mutableListOf<String>()
            val errors = mutableListOf<String>()
            val application = BenchmarkApplication(output::add, errors::add)

            // When
            val status = application.execute(arrayOf("--help"))

            // Then
            assertEquals(0, status)
            assertTrue(output.single().startsWith("Usage:"))
            assertTrue(errors.isEmpty())
        }

    @Test
    fun `invalid command returns usage error`() =
        runTest {
            // Given
            val output = mutableListOf<String>()
            val errors = mutableListOf<String>()
            val application = BenchmarkApplication(output::add, errors::add)

            // When
            val status = application.execute(arrayOf("unknown"))

            // Then
            assertEquals(2, status)
            assertTrue(output.isEmpty())
            assertEquals("error: unknown command: unknown", errors.first())
            assertTrue(errors.last().startsWith("Usage:"))
        }
}
