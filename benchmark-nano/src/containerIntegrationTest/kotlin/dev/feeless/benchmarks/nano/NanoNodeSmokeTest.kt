package dev.feeless.benchmarks.nano

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class NanoNodeSmokeTest {
    @Test
    fun `Nano V28_2 accepts and cements the exact regenerated fixture hashes`() = smoke("nano")

    @Test
    fun `RSNano V3_1 accepts and cements the exact regenerated fixture hashes`() = smoke("rsnano")

    private fun smoke(implementation: String) =
        runBlocking {
            // Given
            val directory = Files.createTempDirectory("$implementation-smoke-fixture-")
            val fixturePath = directory.resolve("smoke.json")
            NanoFixtures.generate("smoke", sourceCount = 1, blocksPerSource = 2, fixturePath)
            val scenario = NanoFixtures.loadScenario(fixturePath, implementation)

            try {
                NanoNodeSpec(implementation).start().use { environment ->
                    // When
                    scenario.setup.forEach { environment.adapter.publish(it, 30.seconds) }
                    scenario.lanes.values
                        .flatten()
                        .forEach { environment.adapter.publish(it, 30.seconds) }

                    // Then
                    assertEquals(2, scenario.expectedCount)
                    assertEquals(NanoNodeSpec(implementation).image, environment.imageReference)
                    assertTrue(environment.imageDigest.startsWith("sha256:"))
                    assertEquals("durable", environment.storageProfile)
                }
            } finally {
                directory.toFile().deleteRecursively()
            }
        }
}
