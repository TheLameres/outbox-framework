package io.coffeeshop.core.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecipeTest {

    private static IngredientRequirement req(String code, String amount, Unit unit) {
        return new IngredientRequirement(code, new BigDecimal(amount), unit);
    }

    private static final Recipe FLAT_WHITE = new Recipe(
            UUID.randomUUID(),
            "Flat White",
            List.of(req("BEANS_ARABICA", "18", Unit.GRAM),
                    req("MILK", "150", Unit.MILLILITER)));

    @Test
    void rejectsEmptyComposition() {
        assertThatThrownBy(() -> new Recipe(UUID.randomUUID(), "Empty", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one ingredient");
    }

    @Test
    void rejectsDuplicateIngredient() {
        assertThatThrownBy(() -> new Recipe(UUID.randomUUID(), "Typo", List.of(
                req("MILK", "150", Unit.MILLILITER),
                req("MILK", "20", Unit.MILLILITER))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate ingredient");
    }

    @Test
    void refusesNonPositiveAmountInRequirement() {
        assertThatThrownBy(() -> req("MILK", "0", Unit.MILLILITER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount must be > 0");
    }

    @Test
    void scalesCompositionForPortions() {
        var requirements = FLAT_WHITE.requirementsFor(3);

        // Сравниваем по значению: у BigDecimal 450 и 4.5E+2 одинаковы по compareTo,
        // но не по equals, поэтому extracting(...).containsExactly здесь не годится.
        assertThat(requirements).hasSize(2);
        assertThat(requirements.get(0).ingredientCode()).isEqualTo("BEANS_ARABICA");
        assertThat(requirements.get(0).amount()).isEqualByComparingTo("54");
        assertThat(requirements.get(1).ingredientCode()).isEqualTo("MILK");
        assertThat(requirements.get(1).amount()).isEqualByComparingTo("450");
        assertThat(requirements.get(1).unit()).isEqualTo(Unit.MILLILITER);
    }

    @Test
    void compositionIsImmutable() {
        var mutable = new java.util.ArrayList<>(List.of(req("MILK", "150", Unit.MILLILITER)));
        var recipe = new Recipe(UUID.randomUUID(), "Latte", mutable);

        mutable.clear();

        assertThat(recipe.ingredients()).hasSize(1);
    }
}
