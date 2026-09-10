package com.somecompany.examples.web.controller;

import com.somecompany.examples.dto.ChangeStatusDto;
import com.somecompany.examples.dto.CreateOrderRequestDto;
import com.somecompany.examples.dto.CreateOrderResponseDto;
import com.somecompany.examples.dto.OrderDto;
import com.somecompany.examples.services.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/{id}")
    public OrderDto getById(@PathVariable UUID id) {
        return orderService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateOrderResponseDto createOrderResponseDto(@RequestBody CreateOrderRequestDto createOrderRequestDto) {
        return orderService.createOrder(createOrderRequestDto);
    }

    @PostMapping("/status")
    public OrderDto createOrderResponseDto(@RequestBody ChangeStatusDto changeStatusDto) {
        return orderService.changeOrderStatus(changeStatusDto);
    }
}
