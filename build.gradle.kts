import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    application
    kotlin("jvm") version "1.4.21"
    kotlin("plugin.serialization") version "1.4.21"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.beryx:text-io:3.4.1")

    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.4.2")
    implementation("io.ktor:ktor-client-cio:1.5.0")
    implementation("io.ktor:ktor-client-serialization-jvm:1.5.0")
    implementation("io.ktor:ktor-client-logging-jvm:1.5.0")

    runtimeOnly("org.slf4j:slf4j-simple:1.8.0-beta4")

    testImplementation("io.kotest:kotest-runner-junit5:4.3.2")
    testImplementation("io.kotest:kotest-assertions-core:4.3.2")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

tasks.compileKotlin {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
    kotlinOptions.freeCompilerArgs = listOf("-Xallow-result-return-type")
}

application {
    mainClass.set("cli.MainKt")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
    systemProperty("org.beryx.textio.TextTerminal", "org.beryx.textio.system.SystemTextTerminal")
}

tasks.withType<Test> {
    useJUnitPlatform()

    testLogging {
        showStandardStreams = true
        exceptionFormat = TestExceptionFormat.FULL
        events(TestLogEvent.STARTED, TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
    }
}

tasks.replace("assemble").dependsOn("installDist")
