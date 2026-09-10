package com.somecompany.examples.entites;

import com.somecompany.examples.dto.OrderStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "orders")
public class OrderEntity extends AbstractEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private BigDecimal sum;

    private String productName;

    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.CREATED;


}
