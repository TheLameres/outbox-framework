package io.txbox.consumer.starter.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Реальные PostgreSQL и Kafka для интеграционных тестов.
 *
 * <p>{@code @ServiceConnection} подставляет адреса контейнеров в
 * {@code spring.datasource.*} и {@code spring.kafka.bootstrap-servers},
 * поэтому тест не задаёт их вручную.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestContainersConfiguration {

    @Bean
    @ServiceConnection
    public PostgreSQLContainer postgreSQLContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"))
                .withUsername("admin")
                .withPassword("admin")
                .withDatabaseName("example");
    }

    @Bean
    @ServiceConnection
    public KafkaContainer kafkaContainer() {
        return new KafkaContainer(DockerImageName.parse("apache/kafka:latest"));
    }
}
