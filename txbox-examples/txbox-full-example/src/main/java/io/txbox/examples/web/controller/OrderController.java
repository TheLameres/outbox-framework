package io.txbox.examples.web.controller;

import io.txbox.examples.dto.CreateOrderRequestDto;
import io.txbox.examples.dto.CreateOrderResponseDto;
import io.txbox.examples.services.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;


    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateOrderResponseDto createOrderResponseDto(@RequestBody CreateOrderRequestDto createOrderRequestDto) {
        return orderService.createOrder(createOrderRequestDto);
    }
}
