plugins {
    kotlin("jvm") version "2.0.0"
    id("maven-publish")
}

group = "ai.dpdpguard"
version = "0.1.0"

repositories { mavenCentral() }

dependencies {
    testImplementation(kotlin("test"))
}
