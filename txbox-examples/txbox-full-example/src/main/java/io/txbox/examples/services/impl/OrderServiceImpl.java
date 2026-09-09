package io.txbox.examples.services.impl;

import io.txbox.examples.dto.CreateOrderRequestDto;
import io.txbox.examples.dto.CreateOrderResponseDto;
import io.txbox.examples.entites.OrderEntity;
import io.txbox.examples.event.OrderEvents;
import io.txbox.examples.repositories.OrderEntityRepository;
import io.txbox.examples.services.OrderService;
import io.txbox.producer.starter.OutboxTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderEntityRepository orderEntityRepository;
    private final OutboxTemplate outboxTemplate;

    @Override
    @Transactional
    public CreateOrderResponseDto createOrder(CreateOrderRequestDto dto) {
        var orderEntity = new OrderEntity();
        orderEntity.setProductName(dto.productName());
        orderEntity.setSum(dto.sum());
        orderEntity.setOccurredAt(Instant.now());

        var saved = orderEntityRepository.save(orderEntity);
        var orderCreated = new OrderEvents.OrderCreated(
                saved.getId(),
                saved.getSum(),
                saved.getProductName(),
                saved.getOccurredAt()
        );
        outboxTemplate.publish(orderCreated, "Order", saved.getId().toString());
        return new CreateOrderResponseDto(saved.getId(),
                saved.getSum(),
                saved.getProductName(),
                saved.getOccurredAt());
    }
}
