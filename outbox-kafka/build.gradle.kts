plugins {
    id("outbox.library-conventions")
    id("outbox.spring-conventions")
    id("outbox.lombok-conventions")   // @Slf4j в KafkaOutboxPublisher
}

description = "Kafka-публикатор outbox-сообщений"

dependencies {
    api(project(":outbox-core"))
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.springframework:spring-context")

    testImplementation("org.springframework.kafka:spring-kafka-test")
}
