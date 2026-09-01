// ВАЖНО: pluginManagement обязан быть ПЕРВЫМ блоком в settings-скрипте.
pluginManagement {
    // includeBuild именно здесь, а не в plugins{} / dependencyResolutionManagement{}:
    // только pluginManagement.includeBuild делает precompiled script plugins
    // из build-logic доступными как id("outbox.*-conventions") во всех модулях.
    includeBuild("build-logic")

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

// Version catalog gradle/libs.versions.toml подхватывается по конвенции
// и регистрирует аксессор `libs`. Отдельный versionCatalogs{} не нужен с Gradle 7.4.
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
