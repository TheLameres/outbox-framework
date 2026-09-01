description = "Kafka-публикатор outbox-сообщений"

dependencies {
    api(project(":outbox-core"))
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.springframework:spring-context")

    testImplementation("org.springframework.kafka:spring-kafka-test")
}
