package io.coffeeshop.order.service;

import java.util.UUID;

/**
 * Запрошенный ресурс не найден.
 *
 * <p>Наследуется от {@link RuntimeException}, а не от {@link IllegalStateException}:
 * это ошибка запроса (HTTP 404), и она не должна попадать в inbox-классификатор.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException menuItem(String code) {
        return new NotFoundException("Menu item not found: " + code);
    }

    public static NotFoundException order(UUID id) {
        return new NotFoundException("Order not found: " + id);
    }
}
