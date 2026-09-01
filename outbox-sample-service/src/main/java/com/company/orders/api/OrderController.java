package com.company.orders.api;

import com.company.orders.domain.OrderService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
