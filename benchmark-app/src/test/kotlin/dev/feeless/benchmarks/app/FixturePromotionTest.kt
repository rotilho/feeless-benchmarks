package dev.feeless.benchmarks.app

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FixturePromotionTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `promotes byte-identical generations`() {
        // Given
        val first = Files.createDirectory(temporaryDirectory.resolve("first"))
        val second = Files.createDirectory(temporaryDirectory.resolve("second"))
        val destination = Files.createDirectory(temporaryDirectory.resolve("fixtures"))
        Files.writeString(first.resolve("fixture.json"), "same\n")
        Files.writeString(second.resolve("fixture.json"), "same\n")

        // When
        val promoted = FixturePromotion.requireIdenticalAndPromote(first, second, destination)

        // Then
        assertEquals(listOf(destination.resolve("fixture.json")), promoted)
        assertEquals("same\n", Files.readString(destination.resolve("fixture.json")))
    }

    @Test
    fun `does not replace canonical data when generations differ`() {
        // Given
        val first = Files.createDirectory(temporaryDirectory.resolve("first"))
        val second = Files.createDirectory(temporaryDirectory.resolve("second"))
        val destination = Files.createDirectory(temporaryDirectory.resolve("fixtures"))
        Files.writeString(first.resolve("fixture.json"), "first\n")
        Files.writeString(second.resolve("fixture.json"), "second\n")
        Files.writeString(destination.resolve("fixture.json"), "canonical\n")

        // When
        val error =
            assertFailsWith<IllegalArgumentException> {
                FixturePromotion.requireIdenticalAndPromote(first, second, destination)
            }

        // Then
        assertEquals("fixture generation is not deterministic: fixture.json", error.message)
        assertEquals("canonical\n", Files.readString(destination.resolve("fixture.json")))
    }
}
