rootProject.name = "build-logic"

// build-logic — отдельная (included) сборка со СВОИМ classpath и своим
// набором каталогов. Переиспользуем тот же TOML-файл, что и основной проект,
// чтобы версии не разъехались между конвенциями и модулями.
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
