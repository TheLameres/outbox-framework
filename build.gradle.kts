import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    alias(libs.plugins.spring.boot) apply false
}

allprojects {
    group = "com.company.outbox"
    version = providers.gradleProperty("outboxVersion").get()
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    // Захватываем значения здесь, пока this == Project.
    // Внутри configure<JavaPluginExtension>{} this переключается на JavaPluginExtension,
    // и libs.* там недоступен — это была причина "Extension 'libs' does not exist".
    val javaVersion    = libs.versions.java.get().toInt()
    val springBootBom  = libs.spring.boot.dependencies
    val jspecifyLib    = libs.jspecify
    val lombokLib      = libs.lombok

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(javaVersion)
            vendor          = JvmVendorSpec.ADOPTIUM
        }
        withSourcesJar()
        withJavadocJar()
    }

    dependencies {
        // BOM как platform вместо плагина io.spring.dependency-management.
        // api(platform(...)) позволяет версиям протекать потребителям библиотеки.
        "api"(platform(springBootBom))
        "annotationProcessor"(platform(springBootBom))
        "testImplementation"(platform(springBootBom))

        // JSpecify: @NullMarked / @Nullable. Spring Boot 4.1 размечен им же.
        "api"(jspecifyLib)

        // Lombok — compileOnly во всех модулях; в build.gradle.kts модуля
        // добавляется только там, где реально нужен (JPA, @Slf4j).
        "compileOnly"(lombokLib)
        "annotationProcessor"(lombokLib)
        "testCompileOnly"(lombokLib)
        "testAnnotationProcessor"(lombokLib)

        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release  = javaVersion
        options.compilerArgs.addAll(
            listOf(
                "-parameters",                         // имена параметров для Spring DI
                "-Xlint:all,-processing,-serial",      // -processing убирает Lombok-варн.
                "-Werror",                             // предупреждения = ошибки сборки
            )
        )
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        systemProperty("junit.jupiter.execution.parallel.enabled", "true")
        systemProperty("junit.jupiter.execution.parallel.mode.default", "concurrent")
        // Mockito inline-mock-maker на JDK 21+ предупреждает о dynamic agent loading
        jvmArgs("-XX:+EnableDynamicAgentLoading", "-Xshare:off")
        testLogging {
            events("passed", "skipped", "failed")
            exceptionFormat = TestExceptionFormat.FULL
        }
    }

    tasks.withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).apply {
            encoding = "UTF-8"
            addStringOption("Xdoclint:none", "-quiet")
            addBooleanOption("html5", true)
        }
    }

    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
                pom {
                    name        = "Outbox Framework :: ${project.name}"
                    description = "Transactional outbox для сервисов компании"
                }
            }
        }
        repositories {
            maven {
                name = "nexus"
                url  = uri(
                    if (version.toString().endsWith("SNAPSHOT"))
                        "https://nexus.company.com/repository/maven-snapshots/"
                    else
                        "https://nexus.company.com/repository/maven-releases/"
                )
                credentials(PasswordCredentials::class)
            }
        }
    }
}

// Агрегирующая задача: собрать и проверить все модули
tasks.register("checkAll") {
    group       = "verification"
    description = "Полная проверка всех модулей фреймворка"
    dependsOn(subprojects.map { "${it.path}:check" })
}
