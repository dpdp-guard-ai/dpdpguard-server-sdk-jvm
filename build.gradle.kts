import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import java.net.URI

plugins {
    kotlin("jvm") version "2.0.0"
    id("maven-publish")
    id("org.openapi.generator") version "7.9.0"
}

group = "ai.dpdpguard"
version = "0.1.0"

repositories { mavenCentral() }

// ADR-002 D2 calls for @dpdpguard/contract to ship with Maven/pub mirrors;
// none exists yet (only the npm package is published). unpkg.com serves the
// exact contents of the published npm package over plain HTTP, so it's the
// interim cross-ecosystem fetch path — swap `contractUrl(...)` for a Maven
// dependency once @dpdpguard:contract ships to Maven Central.
val contractVersion = "0.2.0"
fun contractUrl(path: String) = "https://unpkg.com/@dpdpguard/contract@$contractVersion/$path"

val downloadOpenApiSpec by tasks.registering {
    val outputFile = layout.buildDirectory.file("contract/openapi-v1.yaml")
    outputs.file(outputFile)
    doLast {
        val file = outputFile.get().asFile
        file.parentFile.mkdirs()
        URI(contractUrl("openapi/v1.yaml")).toURL().openStream().use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
    }
}

val generatedDir = layout.buildDirectory.dir("generated/openapi")

openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set(layout.buildDirectory.file("contract/openapi-v1.yaml").map { it.asFile.path })
    outputDir.set(generatedDir.map { it.asFile.path })
    library.set("jvm-okhttp4")
    packageName.set("ai.dpdpguard.server.generated")
    apiPackage.set("ai.dpdpguard.server.generated.api")
    modelPackage.set("ai.dpdpguard.server.generated.model")
    invokerPackage.set("ai.dpdpguard.server.generated.client")
    configOptions.set(
        mapOf(
            "serializationLibrary" to "moshi",
            "dateLibrary" to "java8",
        )
    )
    globalProperties.set(
        mapOf(
            "apiTests" to "false",
            "modelTests" to "false",
            "apiDocs" to "false",
            "modelDocs" to "false",
        )
    )
}

tasks.named<GenerateTask>("openApiGenerate") {
    dependsOn(downloadOpenApiSpec)
}

sourceSets {
    main {
        kotlin.srcDir(generatedDir.map { it.dir("src/main/kotlin") })
    }
}

tasks.named("compileKotlin") {
    dependsOn("openApiGenerate")
}

// Everything under build/generated/openapi is regenerated from the
// installed contract version on every build — never hand-edit it, and
// don't rely on stale output between contract version bumps.
tasks.named("clean") {
    doLast {
        delete(generatedDir)
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    implementation("com.squareup.moshi:moshi-adapters:1.15.1")

    testImplementation(kotlin("test"))
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }
}

kotlin {
    jvmToolchain(17)
}
