package dev.feeless.benchmarks.nano

data class NanoFixtureSpec(
    val fixture: String,
    val sourceCount: Int,
    val blocksPerSource: Int,
) {
    init {
        require(fixture.matches(FIXTURE_NAME)) { "Fixture name must contain lowercase letters, digits, and hyphens" }
        require(sourceCount > 0) { "Source count must be positive" }
        require(blocksPerSource > 0) { "Blocks per source must be positive" }
    }

    val fixtureFileName: String
        get() = "$fixture.json"

    val verificationFileName: String
        get() = "$fixture-verification.json"
}

private val FIXTURE_NAME = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
