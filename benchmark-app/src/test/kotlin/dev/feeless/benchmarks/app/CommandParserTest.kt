package dev.feeless.benchmarks.app

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

class CommandParserTest {
    @Test
    fun `parses a single benchmark run`() {
        // Given
        val arguments =
            arrayOf(
                "run",
                "--implementation=nano",
                "--fixture=nano-genesis",
                "--output-dir",
                "build/result",
                "--timeout-seconds=15",
            )

        // When
        val command = CommandParser.parse(arguments)

        // Then
        assertEquals(
            Command.Run(
                implementation = Implementation.NANO,
                fixture = "nano-genesis",
                outputDirectory = Path.of("build/result"),
                timeout = 15.seconds,
            ),
            command,
        )
    }

    @Test
    fun `suite defaults to every pinned implementation in requested order`() {
        // Given
        val arguments = arrayOf("run-suite", "--output-root=build/suite")

        // When
        val command = CommandParser.parse(arguments) as Command.RunSuite

        // Then
        assertEquals(listOf(Implementation.NANO, Implementation.RSNANO, Implementation.ATTO), command.implementations)
        assertEquals(Path.of("fixtures"), command.fixturesDirectory)
        assertEquals(60.seconds, command.timeout)
    }

    @Test
    fun `aggregate results defaults to ten runs for every implementation`() {
        // Given
        val arguments =
            arrayOf(
                "aggregate-results",
                "--input-root=build/runs",
                "--output-dir=build/aggregate",
            )

        // When
        val command = CommandParser.parse(arguments)

        // Then
        assertEquals(
            Command.AggregateResults(
                inputRoot = Path.of("build/runs"),
                outputDirectory = Path.of("build/aggregate"),
                implementations = listOf(Implementation.NANO, Implementation.ATTO, Implementation.RSNANO),
                expectedRuns = 10,
                accountCount = 500,
            ),
            command,
        )
    }

    @Test
    fun `aggregate results accepts a subset and rejects a nonpositive run count`() {
        // Given
        val valid =
            arrayOf(
                "aggregate-results",
                "--input-root=build/runs",
                "--output-dir=build/aggregate",
                "--implementations=rsnano,nano",
                "--expected-runs=4",
                "--account-count=100",
            )
        val invalid = valid.copyOf().also { arguments -> arguments[4] = "--expected-runs=0" }

        // When
        val command = CommandParser.parse(valid) as Command.AggregateResults
        val error = assertFailsWith<CliException> { CommandParser.parse(invalid) }

        // Then
        assertEquals(listOf(Implementation.RSNANO, Implementation.NANO), command.implementations)
        assertEquals(4, command.expectedRuns)
        assertEquals(100, command.accountCount)
        assertEquals("--expected-runs must be a positive integer", error.message)
    }

    @Test
    fun `aggregate results rejects a nonpositive account count`() {
        // Given
        val arguments =
            arrayOf(
                "aggregate-results",
                "--input-root=build/runs",
                "--output-dir=build/aggregate",
                "--account-count=0",
            )

        // When
        val error = assertFailsWith<CliException> { CommandParser.parse(arguments) }

        // Then
        assertEquals("--account-count must be a positive integer", error.message)
    }

    @Test
    fun `rejects duplicate suite implementations`() {
        // Given
        val arguments =
            arrayOf(
                "run-suite",
                "--implementations=nano,nano",
                "--output-root=build/suite",
            )

        // When
        val error = assertFailsWith<CliException> { CommandParser.parse(arguments) }

        // Then
        assertEquals("implementations must be a non-empty list without duplicates", error.message)
    }

    @Test
    fun `rejects unknown options`() {
        // Given
        val arguments = arrayOf("validate-fixtures", "--unexpected=true")

        // When
        val error = assertFailsWith<CliException> { CommandParser.parse(arguments) }

        // Then
        assertEquals("unknown option(s): --unexpected", error.message)
    }

    @Test
    fun `rejects a nonnumeric timeout`() {
        // Given
        val arguments =
            arrayOf(
                "run",
                "--implementation=atto",
                "--fixture=atto-genesis",
                "--output-dir=build/result",
                "--timeout-seconds=soon",
            )

        // When
        val error = assertFailsWith<CliException> { CommandParser.parse(arguments) }

        // Then
        assertEquals("--timeout-seconds must be a positive integer", error.message)
    }
}
