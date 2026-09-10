package com.somecompany.examples.services;

import com.somecompany.examples.dto.ChangeStatusDto;
import com.somecompany.examples.dto.CreateOrderRequestDto;
import com.somecompany.examples.dto.CreateOrderResponseDto;
import com.somecompany.examples.dto.OrderDto;

import java.util.UUID;

public interface OrderService {
    CreateOrderResponseDto createOrder(CreateOrderRequestDto dto);

    OrderDto findById(UUID id);

    OrderDto changeOrderStatus(ChangeStatusDto changeStatusDto);
}
