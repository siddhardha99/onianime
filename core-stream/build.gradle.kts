plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}

tasks.withType<Test> {
    useJUnit()
    testLogging {
        showStandardStreams = true
        events("passed", "failed", "skipped")
    }
}
