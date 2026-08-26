plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":core"))

    implementation("cash.atto:commons-core:7.0.2")
    implementation("cash.atto:commons-worker:7.0.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("io.ktor:ktor-client-core:3.5.2")
    implementation("io.ktor:ktor-client-apache5:3.5.2")
    implementation("com.mysql:mysql-connector-j:8.4.0")
    implementation("org.testcontainers:testcontainers:2.0.5")
    implementation("org.testcontainers:testcontainers-mysql:2.0.5")

    testImplementation(kotlin("test"))
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
    description = "Runs the Atto Testcontainers smoke test."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = containerIntegrationTestSourceSet.output.classesDirs
    classpath = containerIntegrationTestSourceSet.runtimeClasspath
    shouldRunAfter(tasks.test)
}

tasks.withType<Test>().configureEach {
    systemProperty(
        "fixturesDirectory",
        rootProject.layout.projectDirectory
            .dir("fixtures")
            .asFile.absolutePath,
    )
}
