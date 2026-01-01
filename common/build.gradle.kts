plugins {
    `java-library`
}

dependencies {
    // This is a shared library, no Spring Boot starter needed
    api("jakarta.validation:jakarta.validation-api")

    // Jackson for JSON serialization
    api("com.fasterxml.jackson.core:jackson-databind")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
}

// Disable bootJar since this is a library module
tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

tasks.getByName<Jar>("jar") {
    enabled = true
}
