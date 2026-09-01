plugins {
    id("outbox.library-conventions")
    id("outbox.spring-conventions")
    id("outbox.lombok-conventions")
}

description = "Spring Boot 4 starter: автоконфигурация, поллер на виртуальных потоках, метрики"

dependencies {
    api(project(":outbox-core"))
    api(project(":outbox-jpa"))
    api(project(":outbox-kafka"))

    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.boot:spring-boot-starter-json")
    implementation("org.springframework.boot:spring-boot-actuator")
    implementation("io.micrometer:micrometer-core")

    // Генерирует metadata для автодополнения outbox.* в application.yml.
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
}
