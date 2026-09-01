// ВАЖНО: pluginManagement обязан быть ПЕРВЫМ блоком в settings-скрипте,
// иначе Gradle падает с "pluginManagement {} block must appear before any other statements".
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        // Раскомментировать для milestone-версий Spring Boot (например, 4.2.0-M1)
        // maven("https://repo.spring.io/milestone")
    }
}

plugins {
    // Автоматически скачивает нужный JDK, если его нет локально
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// Version catalog gradle/libs.versions.toml подхватывается автоматически
// по конвенции и регистрирует аксессор `libs`. Отдельного versionCatalogs {}
// блока не требуется начиная с Gradle 7.4.
dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.PREFER_SETTINGS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "outbox-framework"

include(
    "outbox-core",
    "outbox-jpa",
    "outbox-kafka",
    "outbox-spring-boot-starter",
    "outbox-sample-service",
)
