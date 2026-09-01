/**
 * Корневой build-скрипт после перехода на convention plugins.
 *
 * Вся конфигурация (toolchain, компилятор, тесты, публикация, BOM, Lombok)
 * переехала в build-logic/src/main/kotlin/outbox.*.gradle.kts.
 * Ни subprojects{}, ни allprojects{}, ни обращений к libs.* здесь больше нет —
 * каждый модуль сам декларирует нужные конвенции.
 */
tasks.register("checkAll") {
    group = "verification"
    description = "Полная проверка всех модулей фреймворка"
    dependsOn(subprojects.map { "${it.path}:check" })
}
