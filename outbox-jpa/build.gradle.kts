description = "JPA/Hibernate-хранилище outbox с SKIP LOCKED"

dependencies {
    api(project(":outbox-core"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.awaitility:awaitility")
    testRuntimeOnly("org.postgresql:postgresql")
}
