/**
 * Lombok только там, где он нужен: JPA-сущности (Hibernate требует мутабельный
 * класс с no-arg конструктором — record невозможен) и @Slf4j.
 * outbox-core не применяет: там records и sealed-интерфейсы.
 */
plugins {
    id("outbox.java-conventions")
}

// findLibrary вместо the<LibrariesForLibs>(): не завязываемся на конкретные
// сгенерированные accessor-классы для одной зависимости.
val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val lombok = catalog.findLibrary("lombok").orElseThrow()

dependencies {
    // compileOnly, а не implementation: Lombok не должен утечь потребителям стартера.
    compileOnly(lombok)
    annotationProcessor(lombok)
    testCompileOnly(lombok)
    testAnnotationProcessor(lombok)
}
