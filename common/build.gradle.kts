plugins {
    `java-library`
    id("com.github.davidmc24.gradle.plugin.avro") version "1.9.1"
}

dependencies {
    // This is a shared library, no Spring Boot starter needed
    api("jakarta.validation:jakarta.validation-api")

    // Jackson for JSON serialization
    api("com.fasterxml.jackson.core:jackson-databind")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Apache Avro (for schema generation and type-safe DTOs)
    api("org.apache.avro:avro:1.11.3") {
        exclude(group = "org.apache.commons", module = "commons-compress")
    }
    // Use managed commons-compress version
    api("org.apache.commons:commons-compress:1.26.0")
}

// Avro plugin configuration
avro {
    isCreateSetters.set(false)
    isCreateOptionalGetters.set(false)
    isGettersReturnOptional.set(false)
    isOptionalGettersForNullableFieldsOnly.set(false)
    fieldVisibility.set("PRIVATE")
    outputCharacterEncoding.set("UTF-8")
    stringType.set("String")
    templateDirectory.set(null as String?)
    isEnableDecimalLogicalType.set(true)
}

// Disable bootJar since this is a library module
tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

tasks.getByName<Jar>("jar") {
    enabled = true
}

// Ensure Avro classes are generated before compilation
tasks.named("compileJava") {
    dependsOn("generateAvroJava")
}
