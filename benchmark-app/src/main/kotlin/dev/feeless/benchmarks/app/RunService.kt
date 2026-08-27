package dev.feeless.benchmarks.app

import dev.feeless.benchmarks.atto.AttoFixtures
import dev.feeless.benchmarks.atto.AttoNodeSpec
import dev.feeless.benchmarks.core.BenchmarkEngine
import dev.feeless.benchmarks.core.BenchmarkSample
import dev.feeless.benchmarks.nano.NanoFixtures
import dev.feeless.benchmarks.nano.NanoNodeSpec
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration

internal class RunService(
    private val repositoryRoot: Path = Path.of("").toAbsolutePath().normalize(),
    private val report: (String) -> Unit = ::println,
    private val warning: (String) -> Unit = System.err::println,
) {
    private val engine = BenchmarkEngine()

    suspend fun run(command: Command.Run) {
        val outputDirectory = FreshOutputDirectory.create(command.outputDirectory)
        try {
            runScenario(
                implementation = command.implementation,
                fixture = command.fixture,
                fixturesDirectory = repositoryRoot.resolve("fixtures"),
                outputDirectory = outputDirectory,
                outputName = null,
                canonicalExpectedCount = null,
                timeout = command.timeout,
            )
        } catch (error: Throwable) {
            FreshOutputDirectory.removeIfEmpty(outputDirectory)
            throw error
        }
    }

    suspend fun runSuite(command: Command.RunSuite) {
        val outputRoot = createSuiteOutputRoot(command.outputRoot)
        try {
            for (scenario in SuitePlan.scenarios(command.implementations)) {
                val outputDirectory = FreshOutputDirectory.create(outputRoot.resolve(scenario.outputName))
                report("running ${scenario.outputName}")
                try {
                    runScenario(
                        implementation = scenario.implementation,
                        fixture = scenario.fixture,
                        fixturesDirectory = command.fixturesDirectory.toAbsolutePath().normalize(),
                        outputDirectory = outputDirectory,
                        outputName = scenario.outputName,
                        canonicalExpectedCount = scenario.expectedCount,
                        timeout = command.timeout,
                    )
                } catch (error: Throwable) {
                    FreshOutputDirectory.removeIfEmpty(outputDirectory)
                    throw error
                }
            }
        } catch (error: Throwable) {
            FreshOutputDirectory.removeIfEmpty(outputRoot)
            throw error
        }
    }

    private fun createSuiteOutputRoot(path: Path): Path {
        val absolute = path.toAbsolutePath().normalize()
        val absoluteRepositoryRoot = repositoryRoot.toAbsolutePath().normalize()
        if (absoluteRepositoryRoot.startsWith(absolute)) {
            throw CliException("suite output root cannot contain the repository: $absolute")
        }
        return FreshOutputDirectory.createReplacingWithBackup(absolute) { existing, backup ->
            warning("warning: moved existing output root $existing to $backup")
        }
    }

    private suspend fun runScenario(
        implementation: Implementation,
        fixture: String,
        fixturesDirectory: Path,
        outputDirectory: Path,
        outputName: String?,
        canonicalExpectedCount: Int?,
        timeout: Duration,
    ) {
        val completed =
            when (implementation) {
                Implementation.ATTO -> runAtto(fixture, fixturesDirectory, timeout)
                Implementation.NANO,
                Implementation.RSNANO,
                -> runNano(implementation, fixture, fixturesDirectory, timeout)
            }
        val runtimeConfiguration =
            completed.runtimeConfiguration + ("coroutine.dispatcher" to "virtual")
        val stem = resultStem(implementation, completed.fixture, completed.defaultOutputName, outputName)
        val manifest =
            RunManifestFactory.create(
                repositoryRoot = repositoryRoot,
                fixturePaths = completed.fixturePaths,
                imageDigests = completed.imageDigests,
                runtimeConfiguration = runtimeConfiguration,
            )
        check(manifest.runnerRevision != "unknown") { "cannot resolve the benchmark runner revision" }
        RunResultFinalizer.write(
            outputDirectory = outputDirectory,
            stem = stem,
            expectedCount = completed.expectedCount,
            samples = completed.samples,
            manifest = manifest,
            canonicalExpectedCount = canonicalExpectedCount,
            report = report,
        )
    }

    private suspend fun runNano(
        implementation: Implementation,
        fixture: String,
        fixturesDirectory: Path,
        timeout: Duration,
    ): CompletedRun {
        val fixturePath = resolveNanoFixture(fixture, fixturesDirectory)
        val canonicalSpec =
            NanoFixtures.canonicalSpecs.singleOrNull { spec ->
                spec.fixtureFileName == fixturePath.fileName.toString()
            }
        val validation =
            if (canonicalSpec == null) {
                NanoFixtures.validateWithVerification(fixturePath)
            } else {
                NanoFixtures.validateWithVerification(canonicalSpec, fixturePath)
            }
        require(validation.valid) {
            "invalid Nano fixture $fixturePath:\n${validation.errors.joinToString("\n")}"
        }
        val scenario = NanoFixtures.loadScenario(fixturePath, implementation.cliName)
        val environment = NanoNodeSpec(implementation.cliName).start()
        return environment.use {
            val samples = engine.run(scenario, environment.adapter, timeout)
            val defaultOutputName =
                if (implementation == Implementation.RSNANO) {
                    scenario.fixture.replaceFirst(Regex("^nano"), "rsnano")
                } else {
                    scenario.fixture
                }
            CompletedRun(
                fixture = scenario.fixture,
                defaultOutputName = defaultOutputName,
                expectedCount = scenario.expectedCount,
                fixturePaths = listOf(fixturePath),
                imageDigests = mapOf(environment.imageReference to environment.imageDigest),
                runtimeConfiguration =
                    mapOf(
                        "implementation" to implementation.cliName,
                        "fixture" to scenario.fixture,
                        "node.image" to environment.imageReference,
                        "node.network" to "dev",
                        "node.voting_key" to "public dev key installed",
                        "client.rpc.connectionReuse" to "ktor-apache5-pool",
                        "client.rpc.engine" to "ktor-apache5",
                        "client.rpc.maxConnectionsTotal" to "500",
                        "client.rpc.maxConnectionsPerRoute" to "500",
                        "client.rpc.requestTimeout" to "disabled",
                        "client.websocket.engine" to "ktor-cio",
                        "storage.profile" to environment.storageProfile,
                        "rsnano.lmdb.sync" to if (implementation == Implementation.RSNANO) "always" else "not-applicable",
                        "timeout" to timeout.toString(),
                    ),
                samples = samples,
            )
        }
    }

    private suspend fun runAtto(
        fixture: String,
        fixturesDirectory: Path,
        timeout: Duration,
    ): CompletedRun {
        val resolved = resolveAttoFixture(fixture, fixturesDirectory)
        val spec =
            AttoFixtures.canonicalSpecs.singleOrNull { it.fixture == resolved.name }
                ?: throw IllegalArgumentException("unsupported canonical Atto fixture: ${resolved.name}")
        val validation = AttoFixtures.validateWithVerification(spec, resolved.initial, resolved.benchmark)
        require(validation.valid) {
            "invalid Atto fixture ${resolved.name}:\n${validation.errors.joinToString("\n")}"
        }
        val scenario = AttoFixtures.loadScenario(resolved.name, resolved.initial, resolved.benchmark)
        val environment = AttoNodeSpec().start(resolved.initial)
        return environment.use {
            val samples = engine.run(scenario, environment.adapter, timeout)
            CompletedRun(
                fixture = scenario.fixture,
                defaultOutputName = scenario.fixture,
                expectedCount = scenario.expectedCount,
                fixturePaths = listOf(resolved.initial, resolved.benchmark),
                imageDigests = environment.imageDigests,
                runtimeConfiguration =
                    environment.runtimeConfiguration +
                        mapOf(
                            "implementation" to "atto",
                            "fixture" to scenario.fixture,
                            "timeout" to timeout.toString(),
                        ),
                samples = samples,
            )
        }
    }

    private fun resolveNanoFixture(
        fixture: String,
        fixturesDirectory: Path,
    ): Path {
        val explicit = Path.of(fixture)
        val resolved =
            when {
                Files.isRegularFile(explicit) -> explicit
                fixture.endsWith(".json") -> fixturesDirectory.resolve(fixture)
                else -> fixturesDirectory.resolve("$fixture.json")
            }.toAbsolutePath().normalize()
        require(Files.isRegularFile(resolved)) { "Nano fixture does not exist: $resolved" }
        return resolved
    }

    private fun resolveAttoFixture(
        fixture: String,
        fixturesDirectory: Path,
    ): AttoFixturePaths {
        val explicit = Path.of(fixture)
        val name: String
        val initial: Path
        val benchmark: Path
        if (Files.isRegularFile(explicit)) {
            val fileName = explicit.fileName.toString()
            name = fileName.removeSuffix("-initial.zip").removeSuffix("-benchmark.zip")
            require(name != fileName) { "Atto fixture path must end in -initial.zip or -benchmark.zip" }
            initial = explicit.resolveSibling("$name-initial.zip")
            benchmark = explicit.resolveSibling("$name-benchmark.zip")
        } else {
            name = fixture.removeSuffix("-initial.zip").removeSuffix("-benchmark.zip")
            initial = fixturesDirectory.resolve("$name-initial.zip")
            benchmark = fixturesDirectory.resolve("$name-benchmark.zip")
        }
        val resolvedInitial = initial.toAbsolutePath().normalize()
        val resolvedBenchmark = benchmark.toAbsolutePath().normalize()
        require(Files.isRegularFile(resolvedInitial)) { "Atto initial fixture does not exist: $resolvedInitial" }
        require(Files.isRegularFile(resolvedBenchmark)) { "Atto benchmark fixture does not exist: $resolvedBenchmark" }
        return AttoFixturePaths(name, resolvedInitial, resolvedBenchmark)
    }

    private data class AttoFixturePaths(
        val name: String,
        val initial: Path,
        val benchmark: Path,
    )

    private data class CompletedRun(
        val fixture: String,
        val defaultOutputName: String,
        val expectedCount: Int,
        val fixturePaths: List<Path>,
        val imageDigests: Map<String, String>,
        val runtimeConfiguration: Map<String, String>,
        val samples: List<BenchmarkSample>,
    )

    internal companion object {
        fun resultStem(
            implementation: Implementation,
            fixture: String,
            defaultOutputName: String,
            suiteOutputName: String? = null,
        ): String = suiteOutputName ?: CanonicalScenarios.outputName(implementation, fixture) ?: defaultOutputName
    }
}
