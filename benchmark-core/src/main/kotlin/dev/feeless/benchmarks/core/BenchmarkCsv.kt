package dev.feeless.benchmarks.core

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

val RAW_SAMPLE_FIELDS: List<String> =
    listOf(
        "implementation",
        "fixture",
        "lane",
        "sequence",
        "account",
        "hash",
        "start_monotonic_ns",
        "completion_monotonic_ns",
        "latency_ns",
        "error",
    )

fun encodeSamplesCsv(samples: Iterable<BenchmarkSample>): String =
    buildString {
        appendCsvRow(RAW_SAMPLE_FIELDS)
        samples.forEach { sample ->
            appendCsvRow(
                listOf(
                    sample.implementation,
                    sample.fixture,
                    sample.lane,
                    sample.sequence.toString(),
                    sample.account,
                    sample.hash,
                    sample.startMonotonicNs.toString(),
                    sample.completionMonotonicNs?.toString().orEmpty(),
                    sample.latencyNs?.toString().orEmpty(),
                    sample.error.orEmpty(),
                ),
            )
        }
    }

fun decodeSamplesCsv(csv: String): List<BenchmarkSample> {
    val rows = parseCsvRows(csv)
    require(rows.isNotEmpty()) { "samples CSV must contain a header" }
    val expectedHeader = RAW_SAMPLE_FIELDS.joinToString(",")
    require(rows.first() == RAW_SAMPLE_FIELDS) {
        "samples CSV header must be $expectedHeader"
    }

    return rows.drop(1).mapIndexed { index, row ->
        val rowNumber = index + 2
        require(row.size == RAW_SAMPLE_FIELDS.size) {
            "samples CSV row $rowNumber has ${row.size} fields; expected ${RAW_SAMPLE_FIELDS.size}"
        }
        BenchmarkSample(
            implementation = row[0],
            fixture = row[1],
            lane = row[2],
            sequence = row[3].requiredInt("sequence", rowNumber),
            account = row[4],
            hash = row[5],
            startMonotonicNs = row[6].requiredLong("start_monotonic_ns", rowNumber),
            completionMonotonicNs = row[7].optionalLong("completion_monotonic_ns", rowNumber),
            latencyNs = row[8].optionalLong("latency_ns", rowNumber),
            error = row[9].ifEmpty { null },
        )
    }
}

fun writeSamplesCsv(
    path: Path,
    samples: Iterable<BenchmarkSample>,
) {
    writeNewText(path, encodeSamplesCsv(samples))
}

fun readSamplesCsv(path: Path): List<BenchmarkSample> = decodeSamplesCsv(Files.readString(path, StandardCharsets.UTF_8))

private fun StringBuilder.appendCsvRow(fields: List<String>) {
    append(fields.joinToString(",") { field -> field.asCsvField() })
    append("\r\n")
}

private fun String.asCsvField(): String {
    if (none { character -> character == ',' || character == '"' || character == '\r' || character == '\n' }) {
        return this
    }
    return buildString(length + 2) {
        append('"')
        this@asCsvField.forEach { character ->
            if (character == '"') append("\"\"") else append(character)
        }
        append('"')
    }
}

private fun parseCsvRows(csv: String): List<List<String>> {
    if (csv.isEmpty()) return emptyList()

    val rows = mutableListOf<List<String>>()
    val row = mutableListOf<String>()
    val field = StringBuilder()
    var index = 0
    var inQuotes = false
    var closedQuote = false
    var endedWithRowTerminator = false

    fun finishField() {
        row += field.toString()
        field.setLength(0)
        closedQuote = false
    }

    fun finishRow() {
        finishField()
        rows += row.toList()
        row.clear()
        endedWithRowTerminator = true
    }

    while (index < csv.length) {
        val character = csv[index]
        if (inQuotes) {
            if (character == '"') {
                if (index + 1 < csv.length && csv[index + 1] == '"') {
                    field.append('"')
                    index += 1
                } else {
                    inQuotes = false
                    closedQuote = true
                }
            } else {
                field.append(character)
            }
            endedWithRowTerminator = false
            index += 1
            continue
        }

        when (character) {
            '"' -> {
                require(field.isEmpty() && !closedQuote) { "unexpected quote in samples CSV" }
                inQuotes = true
                endedWithRowTerminator = false
            }

            ',' -> {
                finishField()
                endedWithRowTerminator = false
            }

            '\r', '\n' -> {
                finishRow()
                if (character == '\r' && index + 1 < csv.length && csv[index + 1] == '\n') {
                    index += 1
                }
            }

            else -> {
                require(!closedQuote) { "unexpected character after closing quote in samples CSV" }
                field.append(character)
                endedWithRowTerminator = false
            }
        }
        index += 1
    }

    require(!inQuotes) { "unterminated quoted field in samples CSV" }
    if (!endedWithRowTerminator) {
        finishRow()
    }
    return rows
}

private fun String.requiredInt(
    field: String,
    row: Int,
): Int = toIntOrNull() ?: throw IllegalArgumentException("invalid $field in samples CSV row $row: $this")

private fun String.requiredLong(
    field: String,
    row: Int,
): Long = toLongOrNull() ?: throw IllegalArgumentException("invalid $field in samples CSV row $row: $this")

private fun String.optionalLong(
    field: String,
    row: Int,
): Long? = ifEmpty { null }?.requiredLong(field, row)

internal fun writeNewText(
    path: Path,
    content: String,
) {
    path.toAbsolutePath().parent?.let(Files::createDirectories)
    Files.writeString(
        path,
        content,
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE,
    )
}
