plugins {
    // spring-boot-app-conventions сам применяет java-conventions
    // и плагин org.springframework.boot (версия уже на classpath buildSrc).
    id("outbox.spring-boot-app-conventions")
    id("outbox.spring-conventions")
    id("outbox.lombok-conventions")
}

description = "Пример сервиса-потребителя фреймворка"

dependencies {
    implementation(project(":outbox-spring-boot-starter"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.flywaydb:flyway-database-postgresql")

    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.awaitility:awaitility")
}
