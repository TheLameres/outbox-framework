plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    // Кладём артефакт Spring Boot Gradle Plugin на classpath build-logic.
    // Благодаря этому precompiled-скрипт outbox.spring-boot-app-conventions
    // может писать id("org.springframework.boot") БЕЗ версии.
    implementation("org.springframework.boot:spring-boot-gradle-plugin:${libs.versions.springBoot.get()}")
}
