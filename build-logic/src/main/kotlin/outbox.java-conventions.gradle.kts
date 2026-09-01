import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

/**
 * Базовая конвенция: применяется каждым модулем прямо или транзитивно.
 * Здесь только то, что обязано быть одинаковым везде — toolchain, флаги
 * компилятора, запуск тестов, Javadoc. Ничего Spring-специфичного:
 * outbox-core не должен получать Spring транзитивно отсюда.
 */
plugins {
    `java-library`
}

// В precompiled script plugin type-safe аксессор `libs` НЕ генерируется.
// the<LibrariesForLibs>() достаёт тот же каталог по типу, а не по имени.
val libs = the<LibrariesForLibs>()

group = "com.company.outbox"
version = providers.gradleProperty("outboxVersion").getOrElse("0.0.1-SNAPSHOT")

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.versions.java.get().toInt())
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = libs.versions.java.get().toInt()
    options.compilerArgs.addAll(
        listOf(
            "-parameters",                    // имена параметров конструкторов для Spring DI
            "-Xlint:all,-processing,-serial",  // -processing глушит шум от Lombok
            "-Werror",                         // предупреждения = ошибки сборки библиотеки
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
