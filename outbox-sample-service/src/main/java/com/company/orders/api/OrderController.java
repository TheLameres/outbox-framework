package com.company.orders.api;

import com.company.orders.domain.OrderService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** DTO как record с валидацией — @Data/@Builder не нужны. */
    public record CreateOrderRequest(
            @NotNull UUID customerId,
            @NotNull @Positive BigDecimal total) {}

    public record CreateOrderResponse(UUID orderId) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateOrderResponse create(@RequestBody @jakarta.validation.Valid CreateOrderRequest request) {
        return new CreateOrderResponse(
                orderService.createOrder(request.customerId(), request.total()));
    }

    @PostMapping("/{orderId}/cancel")
    public void cancel(@PathVariable UUID orderId, @RequestBody CancelRequest request) {
        orderService.cancelOrder(orderId, request.reason());
    }

    public record CancelRequest(@NotNull String reason) {}
}
