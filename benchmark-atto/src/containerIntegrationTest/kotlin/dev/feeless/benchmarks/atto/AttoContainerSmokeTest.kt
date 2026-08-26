package dev.feeless.benchmarks.atto

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class AttoContainerSmokeTest {
    @Test
    fun `official node cements the exact generated transaction on durable MySQL`() =
        runBlocking {
            // Given
            val spec =
                AttoFixtureSpec(
                    fixture = "atto-container-smoke",
                    accountCount = 1,
                    transactionCount = 1,
                    seed = 17,
                    baseTimestamp = "2026-01-01T00:00:00Z",
                )
            val directory = createTempDirectory("atto-container-smoke-")
            val validation = AttoFixtures.generate(spec, directory)
            assertTrue(validation.valid, validation.errors.joinToString())
            val scenario =
                AttoFixtures.loadScenario(
                    fixture = spec.fixture,
                    initialPath = directory.resolve(spec.initialFileName),
                    benchmarkPath = directory.resolve(spec.benchmarkFileName),
                )
            val item =
                scenario.lanes.values
                    .single()
                    .single()

            // When
            val environment = AttoNodeSpec().start(directory.resolve(spec.initialFileName))
            val storageDirectory = environment.storageDirectory
            environment.use {
                environment.adapter.publish(item, 2.minutes)

                // Then
                assertEquals(setOf(ATTO_NODE_IMAGE, ATTO_MYSQL_IMAGE), environment.imageDigests.keys)
                assertTrue(environment.imageDigests.values.all { it.startsWith("sha256:") })
                assertEquals("durable", environment.runtimeConfiguration["database.storage"])
                assertEquals("mysql-defaults", environment.runtimeConfiguration["database.durability"])
                assertEquals("<redacted>", environment.runtimeConfiguration["database.password"])
            }
            assertTrue(Files.notExists(storageDirectory))
        }
}
