/**
 * Spring Boot BOM + JSpecify для модулей, зависящих от Spring.
 * outbox-core этот плагин НЕ применяет — ядро без Spring.
 */
plugins {
    id("outbox.java-conventions")
}

dependencies {
    // platform() вместо плагина io.spring.dependency-management — Gradle-native
    // способ. api(platform(...)) отдаёт согласованные версии потребителям стартера.
    api(platform(libs.spring.boot.dependencies))
    annotationProcessor(platform(libs.spring.boot.dependencies))
    testImplementation(platform(libs.spring.boot.dependencies))

    // JSpecify: @NullMarked / @Nullable. Spring Boot 4.1 размечен им же.
    api(libs.jspecify)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
