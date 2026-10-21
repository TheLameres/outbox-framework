package io.coffeeshop.barista;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Сервис бариста.
 *
 * <p>Владеет таблицей {@code tickets}. Слушает {@code inventory.reserved},
 * публикует {@code barista.drink-prepared} / {@code barista.drink-failed}.
 */
@SpringBootApplication
public class BaristaServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BaristaServiceApplication.class, args);
    }
}
