/**
 * Корневой build-скрипт после перехода на convention plugins (buildSrc).
 *
 * Вся конфигурация (toolchain, компилятор, тесты, публикация, BOM, Lombok)
 * переехала в buildSrc/src/main/kotlin/outbox.*.gradle.kts.
 * buildSrc Gradle подхватывает АВТОМАТИЧЕСКИ — includeBuild в settings не нужен.
 *
 * Ни subprojects{}, ни allprojects{}, ни обращений к libs.* здесь больше нет.
 */
tasks.register("checkAll") {
    group = "verification"
    description = "Полная проверка всех модулей фреймворка"
    dependsOn(subprojects.map { "${it.path}:check" })
}
