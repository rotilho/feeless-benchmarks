package dev.feeless.benchmarks.app

import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal enum class Implementation {
    ATTO,
    NANO,
    RSNANO,
    ;

    val cliName: String = name.lowercase()

    companion object {
        fun parse(value: String): Implementation =
            entries.firstOrNull { it.cliName == value }
                ?: throw CliException("unsupported implementation: $value")
    }
}

internal enum class FixtureImplementation {
    ALL,
    ATTO,
    NANO,
    ;

    companion object {
        fun parse(value: String): FixtureImplementation =
            entries.firstOrNull { it.name.lowercase() == value }
                ?: throw CliException("unsupported fixture implementation: $value")
    }
}

internal sealed interface Command {
    data object Help : Command

    data class GenerateFixtures(
        val implementation: FixtureImplementation,
        val fixturesDirectory: Path,
    ) : Command

    data class ValidateFixtures(
        val fixturesDirectory: Path,
    ) : Command

    data class Run(
        val implementation: Implementation,
        val fixture: String,
        val outputDirectory: Path,
        val timeout: Duration,
    ) : Command

    data class RunSuite(
        val implementations: List<Implementation>,
        val fixturesDirectory: Path,
        val outputRoot: Path,
        val timeout: Duration,
    ) : Command

    data class AggregateResults(
        val inputRoot: Path,
        val outputDirectory: Path,
        val implementations: List<Implementation>,
        val expectedRuns: Int,
        val accountCount: Int,
    ) : Command
}

internal class CliException(
    message: String,
) : IllegalArgumentException(message)

internal object CommandParser {
    private val defaultFixturesDirectory = Path.of("fixtures")

    fun parse(arguments: Array<String>): Command {
        if (arguments.isEmpty() || arguments.singleOrNull() in setOf("help", "--help", "-h")) {
            return Command.Help
        }

        val command = arguments.first()
        val options = parseOptions(arguments.drop(1))
        return when (command) {
            "generate-fixtures" -> {
                requireOnly(options, "implementation", "fixtures-dir")
                Command.GenerateFixtures(
                    implementation = FixtureImplementation.parse(options["implementation"] ?: "all"),
                    fixturesDirectory = options.path("fixtures-dir", defaultFixturesDirectory),
                )
            }

            "validate-fixtures" -> {
                requireOnly(options, "fixtures-dir")
                Command.ValidateFixtures(
                    fixturesDirectory = options.path("fixtures-dir", defaultFixturesDirectory),
                )
            }

            "run" -> {
                requireOnly(options, "implementation", "fixture", "output-dir", "timeout-seconds")
                Command.Run(
                    implementation = Implementation.parse(options.required("implementation")),
                    fixture = options.required("fixture"),
                    outputDirectory = Path.of(options.required("output-dir")),
                    timeout = options.timeout(),
                )
            }

            "run-suite" -> {
                requireOnly(options, "implementations", "fixtures-dir", "output-root", "timeout-seconds")
                Command.RunSuite(
                    implementations = options.implementations("nano,rsnano,atto"),
                    fixturesDirectory = options.path("fixtures-dir", defaultFixturesDirectory),
                    outputRoot = Path.of(options.required("output-root")),
                    timeout = options.timeout(),
                )
            }

            "aggregate-results" -> {
                requireOnly(options, "input-root", "output-dir", "implementations", "expected-runs", "account-count")
                Command.AggregateResults(
                    inputRoot = Path.of(options.required("input-root")),
                    outputDirectory = Path.of(options.required("output-dir")),
                    implementations = options.implementations("nano,atto,rsnano"),
                    expectedRuns = options.expectedRuns(),
                    accountCount = options.accountCount(),
                )
            }

            else -> throw CliException("unknown command: $command")
        }
    }

    private fun parseOptions(arguments: List<String>): Map<String, String> {
        val options = linkedMapOf<String, String>()
        var index = 0
        while (index < arguments.size) {
            val token = arguments[index]
            if (!token.startsWith("--")) {
                throw CliException("expected an option, got: $token")
            }
            val withoutPrefix = token.removePrefix("--")
            val separator = withoutPrefix.indexOf('=')
            val name: String
            val value: String
            if (separator >= 0) {
                name = withoutPrefix.substring(0, separator)
                value = withoutPrefix.substring(separator + 1)
            } else {
                name = withoutPrefix
                value = arguments
                    .getOrNull(index + 1)
                    ?.takeUnless { it.startsWith("--") }
                    ?: throw CliException("missing value for --$name")
                index += 1
            }
            if (name.isEmpty() || value.isEmpty()) {
                throw CliException("invalid option: $token")
            }
            if (options.put(name, value) != null) {
                throw CliException("duplicate option: --$name")
            }
            index += 1
        }
        return options
    }

    private fun requireOnly(
        options: Map<String, String>,
        vararg allowed: String,
    ) {
        val unknown = options.keys - allowed.toSet()
        if (unknown.isNotEmpty()) {
            throw CliException("unknown option(s): ${unknown.sorted().joinToString { "--$it" }}")
        }
    }

    private fun Map<String, String>.required(name: String): String = this[name] ?: throw CliException("missing required option: --$name")

    private fun Map<String, String>.path(
        name: String,
        default: Path,
    ): Path = this[name]?.let(Path::of) ?: default

    private fun Map<String, String>.implementations(default: String): List<Implementation> {
        val implementations =
            (this["implementations"] ?: default)
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .map(Implementation::parse)
        if (implementations.isEmpty() || implementations.size != implementations.distinct().size) {
            throw CliException("implementations must be a non-empty list without duplicates")
        }
        return implementations
    }

    private fun Map<String, String>.expectedRuns(): Int {
        val raw = this["expected-runs"] ?: return 10
        val expectedRuns = raw.toIntOrNull() ?: throw CliException("--expected-runs must be a positive integer")
        if (expectedRuns <= 0) throw CliException("--expected-runs must be a positive integer")
        return expectedRuns
    }

    private fun Map<String, String>.accountCount(): Int {
        val raw = this["account-count"] ?: return 500
        val accountCount = raw.toIntOrNull() ?: throw CliException("--account-count must be a positive integer")
        if (accountCount <= 0) throw CliException("--account-count must be a positive integer")
        return accountCount
    }

    private fun Map<String, String>.timeout(): Duration {
        val raw = this["timeout-seconds"] ?: return 60.seconds
        val seconds =
            raw.toLongOrNull()
                ?: throw CliException("--timeout-seconds must be a positive integer")
        if (seconds <= 0L) {
            throw CliException("--timeout-seconds must be a positive integer")
        }
        return seconds.seconds
    }
}
