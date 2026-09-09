package io.txbox.examples.services.impl;

import io.txbox.examples.dto.*;
import io.txbox.examples.entites.OrderEntity;
import io.txbox.examples.event.OrderEvents;
import io.txbox.examples.exceptions.NotFoundException;
import io.txbox.examples.repositories.OrderEntityRepository;
import io.txbox.examples.services.OrderService;
import io.txbox.producer.starter.OutboxTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

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

        orderEntityRepository.save(orderEntity);
        var orderCreated = new OrderEvents.OrderCreated(
                orderEntity.getId(),
                orderEntity.getSum(),
                orderEntity.getProductName(),
                orderEntity.getCreatedAt()
        );
        outboxTemplate.publish(orderCreated, "Order", orderEntity.getId().toString());
        return new CreateOrderResponseDto(orderEntity.getId(),
                orderEntity.getCreatedAt(),
                orderEntity.getStatus()
        );
    }

    @Override
    public OrderDto findById(UUID id) {
        return orderEntityRepository.findById(id)
                .map(it -> new OrderDto(
                        it.getId(),
                        it.getSum(),
                        it.getProductName(),
                        it.getCreatedAt(),
                        it.getStatus()
                )).orElseThrow(() -> new NotFoundException("Order with id %s not found".formatted(id)));
    }

    @Override
    @Transactional
    public OrderDto changeOrderStatus(ChangeStatusDto changeStatusDto) {
        var orderEntity = orderEntityRepository.findById(changeStatusDto.id())
                .orElseThrow(() -> new NotFoundException("Order with id %s not found"
                        .formatted(changeStatusDto.id())));
        orderEntity.setStatus(changeStatusDto.status());
        orderEntityRepository.save(orderEntity);
        var event = new OrderEvents.OrderChangeStatus(orderEntity.getId(),
                orderEntity.getStatus(),
                orderEntity.getUpdatedAt());
        outboxTemplate.publish(event,
                "Order",
                orderEntity.getId().toString());
        return new OrderDto(orderEntity.getId(),
                orderEntity.getSum(),
                orderEntity.getProductName(),
                orderEntity.getCreatedAt(),
                orderEntity.getStatus());
    }


}
