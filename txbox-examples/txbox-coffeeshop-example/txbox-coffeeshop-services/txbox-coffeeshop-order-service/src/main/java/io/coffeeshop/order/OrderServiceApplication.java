package io.coffeeshop.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Сервис заказов.
 *
 * <p>Владеет таблицами {@code orders} и {@code order_lines}. Обе стороны паттерна живут
 * в одном приложении: outbox пишет {@code order.placed}, inbox читает
 * {@code inventory.*} и {@code barista.*}.
 *
 * <p>Репозитории объявлены явно. Стартеры txbox тоже используют
 * {@code @EnableJpaRepositories} — со своими пакетами. Несколько таких аннотаций
 * с непересекающимися пакетами Spring Data обрабатывает штатно, зато пакет сервиса
 * сканируется независимо от порядка автоконфигураций.
 */
@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
