package io.coffeeshop.order.repository;

import io.coffeeshop.order.entity.MenuItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MenuItemRepository extends JpaRepository<MenuItemEntity, UUID> {

    Optional<MenuItemEntity> findByCode(String code);

    List<MenuItemEntity> findAllByOrderByCodeAsc();
}
