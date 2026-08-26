package dev.feeless.benchmarks.app

import dev.feeless.benchmarks.core.VIRTUAL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

fun main(arguments: Array<String>) {
    val status = runBlocking(Dispatchers.VIRTUAL) { BenchmarkApplication().execute(arguments) }
    if (status != 0) exitProcess(status)
}

internal class BenchmarkApplication(
    private val output: (String) -> Unit = ::println,
    private val error: (String) -> Unit = System.err::println,
) {
    suspend fun execute(arguments: Array<String>): Int =
        try {
            when (val command = CommandParser.parse(arguments)) {
                Command.Help -> output(usage)
                is Command.AggregateResults -> AggregateResultsService(output).aggregate(command)
                is Command.GenerateFixtures -> FixtureService(output).generate(command)
                is Command.ValidateFixtures -> FixtureService(output).validate(command)
                is Command.Run -> RunService(report = output).run(command)
                is Command.RunSuite -> RunService(report = output).runSuite(command)
            }
            0
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: CliException) {
            error("error: ${exception.message}")
            error(usage)
            2
        } catch (exception: Exception) {
            error("error: ${exception.message ?: exception.javaClass.simpleName}")
            1
        }
}

private val usage =
    """
    Usage:
      generate-fixtures --implementation=all|atto|nano [--fixtures-dir=fixtures]
      validate-fixtures [--fixtures-dir=fixtures]
      aggregate-results --input-root=... --output-dir=... [--implementations=nano,atto,rsnano] [--expected-runs=10] [--account-count=500]
      run --implementation=atto|nano|rsnano --fixture=... --output-dir=... [--timeout-seconds=60]
      run-suite [--implementations=nano,rsnano,atto] [--fixtures-dir=fixtures] --output-root=... [--timeout-seconds=60]
    """.trimIndent()
