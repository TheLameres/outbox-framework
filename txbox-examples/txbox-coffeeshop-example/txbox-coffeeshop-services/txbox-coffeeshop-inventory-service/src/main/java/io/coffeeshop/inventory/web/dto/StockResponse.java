package io.coffeeshop.inventory.web.dto;

import io.coffeeshop.inventory.entity.StockLevelEntity;

import java.math.BigDecimal;

public record StockResponse(String ingredientCode,
                            String unit,
                            BigDecimal onHand,
                            BigDecimal reserved,
                            BigDecimal available) {

    public static StockResponse from(StockLevelEntity entity) {
        return new StockResponse(
                entity.getIngredientCode(),
                entity.getUnit().name(),
                entity.getOnHand(),
                entity.getReserved(),
                entity.getOnHand().subtract(entity.getReserved()));
    }
}
