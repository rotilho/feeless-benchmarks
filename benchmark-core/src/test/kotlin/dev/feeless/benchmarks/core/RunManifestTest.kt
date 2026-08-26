package dev.feeless.benchmarks.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RunManifestTest {
    @Test
    fun `manifest JSON round trip preserves all required provenance`() {
        // Given
        val manifest = manifest(mapOf("z" to "last", "a" to "first"))

        // When
        val encoded = encodeRunManifestJson(manifest)
        val decoded = decodeRunManifestJson(encoded)

        // Then
        assertEquals(manifest, decoded)
        assertTrue(encoded.contains("\"runner_revision\""))
        assertTrue(encoded.contains("\"fixture_hashes\""))
        assertTrue(encoded.contains("\"image_digests\""))
        assertTrue(encoded.contains("\"operating_system\""))
        assertTrue(encoded.contains("\"storage_profile\""))
        assertTrue(encoded.contains("\"runtime_configuration\""))
        assertTrue(encoded.endsWith('\n'))
    }

    @Test
    fun `manifest JSON sorts mappings for deterministic output`() {
        // Given
        val forward = manifest(linkedMapOf("a" to "first", "z" to "last"))
        val reverse = manifest(linkedMapOf("z" to "last", "a" to "first"))

        // When
        val forwardJson = encodeRunManifestJson(forward)
        val reverseJson = encodeRunManifestJson(reverse)

        // Then
        assertEquals(forwardJson, reverseJson)
    }

    private fun manifest(runtimeConfiguration: Map<String, String>): RunManifest =
        RunManifest(
            runnerRevision = "abc123",
            fixtureHashes = linkedMapOf("z.json" to "sha256:z", "a.json" to "sha256:a"),
            imageDigests = linkedMapOf("rsnano" to "sha256:r", "nano" to "sha256:n"),
            java = JavaRuntimeDetails(vendor = "vendor", version = "21", vmName = "vm", vmVersion = "21.0.1"),
            operatingSystem = OperatingSystemDetails(architecture = "amd64", name = "Linux", version = "1"),
            cpu = CpuDetails(logicalProcessorCount = 8, model = null),
            storageProfile = "durable",
            runtimeConfiguration = runtimeConfiguration,
        )
}
