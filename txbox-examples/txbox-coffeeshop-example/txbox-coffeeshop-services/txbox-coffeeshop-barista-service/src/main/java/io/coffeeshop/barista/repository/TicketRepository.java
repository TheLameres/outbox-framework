package io.coffeeshop.barista.repository;

import io.coffeeshop.barista.entity.TicketEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<TicketEntity, UUID> {

    Optional<TicketEntity> findByOrderId(UUID orderId);

    boolean existsByOrderId(UUID orderId);
}
