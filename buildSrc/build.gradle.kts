plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    // Spring Boot Gradle Plugin на classpath buildSrc. Благодаря этому
    // precompiled-скрипт outbox.spring-boot-app-conventions может писать
    // id("org.springframework.boot") БЕЗ явной версии.
    implementation("org.springframework.boot:spring-boot-gradle-plugin:${libs.versions.springBoot.get()}")
}
