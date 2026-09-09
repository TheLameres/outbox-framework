package io.txbox.examples.repositories;

import io.txbox.examples.entites.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderEntityRepository extends JpaRepository<OrderEntity, UUID> {
}