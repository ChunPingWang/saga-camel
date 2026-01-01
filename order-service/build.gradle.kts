dependencies {
    implementation(project(":common"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Apache Camel
    implementation("org.apache.camel.springboot:camel-spring-boot-starter:4.3.0")
    implementation("org.apache.camel.springboot:camel-http-starter:4.3.0")
    implementation("org.apache.camel.springboot:camel-jackson-starter:4.3.0")

    // Database
    runtimeOnly("com.h2database:h2")

    // OpenAPI / Swagger
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")

    // Micrometer for metrics
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Testing
    testImplementation("org.apache.camel:camel-test-spring-junit5:4.3.0")
    testImplementation("org.awaitility:awaitility:4.2.0")
}
