package io.coffeeshop.inventory.repository;

import io.coffeeshop.inventory.entity.ReservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<ReservationEntity, UUID> {

    List<ReservationEntity> findByOrderId(UUID orderId);

    boolean existsByOrderIdAndIngredientCode(UUID orderId, String ingredientCode);
}
