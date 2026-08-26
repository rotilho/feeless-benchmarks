plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":core"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("io.ktor:ktor-client-apache5:3.5.2")
    implementation("io.ktor:ktor-client-core:3.5.2")
    implementation("io.ktor:ktor-client-cio:3.5.2")
    implementation("io.ktor:ktor-client-content-negotiation:3.5.2")
    implementation("io.ktor:ktor-client-websockets:3.5.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.2")
    implementation("org.bouncycastle:bcprov-jdk18on:1.84")
    implementation("net.i2p.crypto:eddsa:0.3.0")
    implementation("org.testcontainers:testcontainers:2.0.5")

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("io.ktor:ktor-client-mock:3.5.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val containerIntegrationTestSourceSet = sourceSets.create("containerIntegrationTest")
containerIntegrationTestSourceSet.compileClasspath += sourceSets.main.get().output
containerIntegrationTestSourceSet.runtimeClasspath += sourceSets.main.get().output

configurations[containerIntegrationTestSourceSet.implementationConfigurationName]
    .extendsFrom(configurations.testImplementation.get())
configurations[containerIntegrationTestSourceSet.runtimeOnlyConfigurationName]
    .extendsFrom(configurations.testRuntimeOnly.get())

tasks.register<Test>("containerIntegrationTest") {
    description = "Runs Nano and RSNano Testcontainers smoke tests."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = containerIntegrationTestSourceSet.output.classesDirs
    classpath = containerIntegrationTestSourceSet.runtimeClasspath
    shouldRunAfter(tasks.test)
}
