package io.txbox.examples.services;

import io.txbox.examples.dto.CreateOrderRequestDto;
import io.txbox.examples.dto.CreateOrderResponseDto;

public interface OrderService {
    CreateOrderResponseDto createOrder(CreateOrderRequestDto dto);
}
