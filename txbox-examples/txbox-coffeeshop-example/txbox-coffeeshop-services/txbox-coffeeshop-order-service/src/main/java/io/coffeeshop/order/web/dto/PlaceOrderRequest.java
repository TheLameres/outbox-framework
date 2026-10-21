package io.coffeeshop.order.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Запрос на оформление заказа.
 *
 * <p>Гость присылает коды позиций меню, а не идентификаторы: код — стабильный
 * естественный ключ, понятный человеку и не зависящий от суррогатных UUID в БД.
 */
public record PlaceOrderRequest(
        @NotEmpty(message = "order must contain at least one line")
        List<@Valid Line> lines) {

    public record Line(
            @NotBlank(message = "menuItemCode is required")
            String menuItemCode,

            @Min(value = 1, message = "quantity must be >= 1")
            int quantity) {
    }
}
