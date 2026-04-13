plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.compose") version "2.1.0"
    application
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation("com.jakewharton.mosaic:mosaic-runtime:0.18.0")
    implementation("com.jakewharton.mosaic:mosaic-tty:0.18.0")
    implementation("com.jakewharton.mosaic:mosaic-tty-terminal:0.18.0")
    implementation("com.jakewharton.mosaic:mosaic-terminal:0.18.0")
    implementation("com.github.docker-java:docker-java-core:3.4.1")
    implementation("com.github.docker-java:docker-java-transport-httpclient5:3.4.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.slf4j:slf4j-simple:2.0.16")
}

application {
    mainClass.set("dev.dockerdashboard.MainKt")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

kotlin {
    jvmToolchain(17)
}
