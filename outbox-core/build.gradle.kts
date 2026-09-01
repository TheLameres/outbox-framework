plugins {
    // library-conventions транзитивно тащит java-conventions
    // (toolchain, -Werror, тесты, sources/javadoc jar, публикация).
    id("outbox.library-conventions")
}

description = "Ядро: records, sealed-интерфейсы, контракты. Без Spring."

dependencies {
    // Сознательно НЕ применяем outbox.spring-conventions:
    // ядро не должно зависеть от Spring ни одним классом.
    testImplementation("org.assertj:assertj-core")
}
