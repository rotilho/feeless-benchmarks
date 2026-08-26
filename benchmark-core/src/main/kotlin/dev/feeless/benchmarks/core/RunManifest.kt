package dev.feeless.benchmarks.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

@Serializable
data class JavaRuntimeDetails(
    val vendor: String,
    val version: String,
    @SerialName("vm_name")
    val vmName: String,
    @SerialName("vm_version")
    val vmVersion: String,
)

@Serializable
data class OperatingSystemDetails(
    val architecture: String,
    val name: String,
    val version: String,
)

@Serializable
data class CpuDetails(
    @SerialName("logical_processor_count")
    val logicalProcessorCount: Int,
    val model: String?,
)

@Serializable
data class RunManifest(
    @SerialName("runner_revision")
    val runnerRevision: String,
    @SerialName("fixture_hashes")
    val fixtureHashes: Map<String, String>,
    @SerialName("image_digests")
    val imageDigests: Map<String, String>,
    val java: JavaRuntimeDetails,
    @SerialName("operating_system")
    val operatingSystem: OperatingSystemDetails,
    val cpu: CpuDetails,
    @SerialName("storage_profile")
    val storageProfile: String,
    @SerialName("runtime_configuration")
    val runtimeConfiguration: Map<String, String>,
)

private val manifestJson =
    Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
        prettyPrint = true
        prettyPrintIndent = "  "
    }

fun encodeRunManifestJson(manifest: RunManifest): String = manifestJson.encodeToString(manifest.withSortedMappings()) + "\n"

fun decodeRunManifestJson(json: String): RunManifest = manifestJson.decodeFromString(json)

fun writeRunManifestJson(
    path: Path,
    manifest: RunManifest,
) {
    writeNewText(path, encodeRunManifestJson(manifest))
}

fun readRunManifestJson(path: Path): RunManifest = decodeRunManifestJson(Files.readString(path, StandardCharsets.UTF_8))

private fun RunManifest.withSortedMappings(): RunManifest =
    copy(
        fixtureHashes = fixtureHashes.toSortedMap(),
        imageDigests = imageDigests.toSortedMap(),
        runtimeConfiguration = runtimeConfiguration.toSortedMap(),
    )
