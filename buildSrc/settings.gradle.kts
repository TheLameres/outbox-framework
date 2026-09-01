rootProject.name = "buildSrc"

// buildSrc — не composite build, а авто-распознаваемая папка. У НЕЁ НЕТ
// доступа к version catalog основного проекта по умолчанию, поэтому
// объявляем собственный каталог, указывающий на ТОТ ЖЕ файл ../gradle/libs.versions.toml.
dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
