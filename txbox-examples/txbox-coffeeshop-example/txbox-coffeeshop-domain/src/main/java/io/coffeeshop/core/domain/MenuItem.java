package io.coffeeshop.core.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Позиция меню.
 *
 * <p>Поле {@code isAvailable} здесь намеренно отсутствует. Доступность — это функция
 * остатков на складе, а не свойство карточки меню: если хранить её копией, появятся
 * два источника истины, которые неизбежно разойдутся. Считать доступность должен
 * сервис склада по рецепту ({@link Recipe}) и текущим остаткам.
 *
 * @param id          идентификатор позиции
 * @param code        стабильный код, например {@code FLAT_WHITE}
 * @param name        название для гостя
 * @param description описание для меню
 * @param price       цена одной порции
 * @param recipeId    ссылка на рецепт
 */
public record MenuItem(UUID id,
                       String code,
                       String name,
                       String description,
                       Money price,
                       UUID recipeId) {

    public MenuItem {
        Objects.requireNonNull(id, "id");
        code = Validate.requireText(code, "code");
        name = Validate.requireText(name, "name");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(recipeId, "recipeId");
    }
}
