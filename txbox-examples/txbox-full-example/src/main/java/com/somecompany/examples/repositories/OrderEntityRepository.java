package com.somecompany.examples.repositories;

import com.somecompany.examples.entites.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderEntityRepository extends JpaRepository<OrderEntity, UUID> {
}