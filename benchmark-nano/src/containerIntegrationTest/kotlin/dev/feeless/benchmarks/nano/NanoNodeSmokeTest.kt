package dev.feeless.benchmarks.nano

import com.github.dockerjava.api.exception.NotFoundException
import kotlinx.coroutines.runBlocking
import org.testcontainers.DockerClientFactory
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class NanoNodeSmokeTest {
    @Test
    fun `Nano V28_2 accepts and cements the exact regenerated fixture hashes`() = smoke("nano")

    @Test
    fun `RSNano latest accepts and cements the exact regenerated fixture hashes`() = smoke("rsnano")

    private fun smoke(implementation: String) =
        runBlocking<Unit> {
            // Given
            val directory = Files.createTempDirectory("$implementation-smoke-fixture-")
            val fixturePath = directory.resolve("smoke.json")
            NanoFixtures.generate("smoke", sourceCount = 1, blocksPerSource = 2, fixturePath)
            val scenario = NanoFixtures.loadScenario(fixturePath, implementation)

            try {
                val storageVolumeName =
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
                        environment.storageVolumeName
                    }

                // Then
                assertFailsWith<NotFoundException> {
                    DockerClientFactory
                        .instance()
                        .client()
                        .inspectVolumeCmd(storageVolumeName)
                        .exec()
                }
            } finally {
                directory.toFile().deleteRecursively()
            }
        }
}
