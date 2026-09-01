/**
 * Для исполняемых Spring Boot приложений (outbox-sample-service).
 * Применяется вместе с outbox.spring-conventions и outbox.lombok-conventions.
 */
plugins {
    id("outbox.java-conventions")
    // Версия НЕ указывается: артефакт плагина уже на classpath build-logic.
    id("org.springframework.boot")
}

// Приложению нужен только bootJar; обычный jar как библиотеку не публикуем.
tasks.named<Jar>("jar") {
    enabled = false
}
