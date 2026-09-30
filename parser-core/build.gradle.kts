plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin: no Android dependencies, so the parser runs and is tested on the plain JVM.
kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(platform(libs.junit5.bom))
    testImplementation(libs.junit5.jupiter)
    testImplementation(libs.junit5.params)
    testImplementation(libs.kotlinx.coroutines.core)
    testRuntimeOnly(libs.junit5.platform.launcher)
}

tasks.test {
    useJUnitPlatform {
        excludeTags("perf")
    }
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}

// Performance checks are timing-sensitive, so they run on demand: ./gradlew :parser-core:perfTest
tasks.register<Test>("perfTest") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("perf") }
    testLogging { showStandardStreams = true }
}
