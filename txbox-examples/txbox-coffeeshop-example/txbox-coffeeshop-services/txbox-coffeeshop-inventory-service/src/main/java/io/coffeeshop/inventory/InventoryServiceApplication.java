package io.coffeeshop.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Сервис склада.
 *
 * <p>Владеет таблицами {@code ingredients}, {@code stock_levels}, {@code reservations}.
 * Слушает {@code order.placed} и {@code order.cancelled}, публикует
 * {@code inventory.reserved} / {@code inventory.rejected} / {@code inventory.released}.
 */
@SpringBootApplication
public class InventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}
