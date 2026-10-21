package io.coffeeshop.order.entity;

import io.coffeeshop.core.domain.IngredientRequirement;
import io.coffeeshop.core.domain.MenuItem;
import io.coffeeshop.core.domain.Money;
import io.coffeeshop.core.domain.Unit;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Позиция меню вместе с рецептом.
 *
 * <p>Рецепт хранится здесь, в сервисе заказов, а не в сервисе склада. Причина:
 * {@code order.placed} несёт снимок состава, и склад резервирует ровно то,
 * что пришло в событии. Так склад не зависит от доступности сервиса меню
 * в момент обработки — иначе асинхронная сага деградировала бы до синхронной.
 */
@Entity
@Table(name = "menu_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MenuItemEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "price_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceAmount;

    @Column(name = "price_currency", nullable = false, length = 3)
    private String priceCurrency;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private List<MenuItemIngredientEntity> ingredients = new ArrayList<>();

    public MenuItem toDomain() {
        return new MenuItem(
                id,
                code,
                name,
                description,
                new Money(new BigDecimal(priceAmount.toPlainString()), priceCurrency),
                id);
    }

    /**
     * Состав на одну порцию — то, что уезжает в {@code order.placed}.
     */
    public List<IngredientRequirement> requirements() {
        return ingredients.stream()
                .map(i -> new IngredientRequirement(
                        i.getIngredientCode(), i.getAmount(), i.getUnit()))
                .toList();
    }

    public Money price() {
        return new Money(new BigDecimal(priceAmount.toPlainString()), priceCurrency);
    }
}
