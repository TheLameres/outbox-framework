package io.txbox.examples.services;

import io.txbox.examples.dto.*;

import java.util.Optional;
import java.util.UUID;

public interface OrderService {
    CreateOrderResponseDto createOrder(CreateOrderRequestDto dto);

    OrderDto findById(UUID id);

    OrderDto changeOrderStatus(ChangeStatusDto changeStatusDto);
}
