package io.coffeeshop.inventory.repository;

import io.coffeeshop.inventory.entity.IngredientEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngredientRepository extends JpaRepository<IngredientEntity, String> {
}
