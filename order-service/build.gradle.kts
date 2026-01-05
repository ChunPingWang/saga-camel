plugins {
    scala
    id("io.gatling.gradle") version "3.10.3.2"
}

dependencies {
    implementation(project(":common"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // Apache Camel
    implementation("org.apache.camel.springboot:camel-spring-boot-starter:4.3.0")
    implementation("org.apache.camel.springboot:camel-http-starter:4.3.0")
    implementation("org.apache.camel.springboot:camel-jackson-starter:4.3.0")

    // Resilience4j (Circuit Breaker, Retry, Bulkhead)
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.2.0")
    implementation("io.github.resilience4j:resilience4j-retry:2.2.0")
    implementation("io.github.resilience4j:resilience4j-bulkhead:2.2.0")
    implementation("io.github.resilience4j:resilience4j-all:2.2.0")

    // Database
    runtimeOnly("com.h2database:h2")
    runtimeOnly("org.postgresql:postgresql")

    // Flyway for migrations
    implementation("org.flywaydb:flyway-core")

    // Kafka
    implementation("org.springframework.kafka:spring-kafka")

    // OpenAPI / Swagger
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")

    // Micrometer for metrics
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Testing
    testImplementation("org.apache.camel:camel-test-spring-junit5:4.3.0")
    testImplementation("org.awaitility:awaitility:4.2.0")

    // Testcontainers for integration testing
    testImplementation("org.testcontainers:testcontainers:1.19.3")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
    testImplementation("org.testcontainers:kafka:1.19.3")
    testImplementation("org.testcontainers:postgresql:1.19.3")
    testImplementation("org.springframework.kafka:spring-kafka-test")

    // Gatling for load testing
    gatling("io.gatling.highcharts:gatling-charts-highcharts:3.10.3")
    gatling("io.gatling:gatling-http:3.10.3")
}

gatling {
    // Gatling configuration
    logLevel = "WARN"
}
