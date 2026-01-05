plugins {
    java
    id("org.springframework.boot") version "3.2.0" apply false
    id("io.spring.dependency-management") version "1.1.4" apply false
}

allprojects {
    group = "com.ecommerce"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
        // Confluent repository for Kafka Avro serializer
        maven {
            url = uri("https://packages.confluent.io/maven/")
        }
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "jacoco")

    java {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    dependencies {
        // Common dependencies for all subprojects
        "implementation"("org.springframework.boot:spring-boot-starter")
        "implementation"("org.springframework.boot:spring-boot-starter-validation")

        // Lombok
        "compileOnly"("org.projectlombok:lombok")
        "annotationProcessor"("org.projectlombok:lombok")

        // Testing
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
        "testCompileOnly"("org.projectlombok:lombok")
        "testAnnotationProcessor"("org.projectlombok:lombok")

        // BDD - Cucumber
        "testImplementation"("io.cucumber:cucumber-java:7.15.0")
        "testImplementation"("io.cucumber:cucumber-spring:7.15.0")
        "testImplementation"("io.cucumber:cucumber-junit-platform-engine:7.15.0")
        "testImplementation"("org.junit.platform:junit-platform-suite:1.10.1")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        finalizedBy(tasks.named("jacocoTestReport"))
    }

    tasks.withType<JacocoReport> {
        dependsOn(tasks.named("test"))
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }

    tasks.withType<JacocoCoverageVerification> {
        dependsOn(tasks.named("jacocoTestReport"))
        violationRules {
            rule {
                limit {
                    minimum = "0.80".toBigDecimal()
                }
            }
            // Domain layer coverage
            rule {
                element = "PACKAGE"
                includes = listOf(
                    "com.ecommerce.*.domain.*",
                    "com.ecommerce.*.application.*"
                )
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = "0.80".toBigDecimal()
                }
            }
        }
    }
}
