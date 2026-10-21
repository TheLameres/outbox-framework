package io.coffeeshop.order.web.dto;

import io.coffeeshop.core.domain.Money;

public record MoneyDto(String amount, String currency) {

    public static MoneyDto from(Money money) {
        return new MoneyDto(money.amount().toPlainString(), money.currency());
    }
}
