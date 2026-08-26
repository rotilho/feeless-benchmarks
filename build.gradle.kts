plugins {
    kotlin("jvm") version "2.4.10" apply false
    kotlin("plugin.serialization") version "2.4.10" apply false
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0" apply false
}

allprojects {
    group = "dev.feeless.benchmarks"
    version = "1.0.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

tasks.register("containerIntegrationTest") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs the opt-in Testcontainers integration tests."
    dependsOn(
        ":atto:containerIntegrationTest",
        ":nano:containerIntegrationTest",
    )
}

gradle.projectsEvaluated {
    val nanoSmoke = project(":nano").tasks.named("containerIntegrationTest")
    project(":atto").tasks.named("containerIntegrationTest") {
        mustRunAfter(nanoSmoke)
    }
}
