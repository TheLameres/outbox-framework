package io.coffeeshop.order.web;

import io.coffeeshop.order.service.OrderService;
import io.coffeeshop.order.web.dto.CancelOrderRequest;
import io.coffeeshop.order.web.dto.OrderResponse;
import io.coffeeshop.order.web.dto.PlaceOrderRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse place(@Valid @RequestBody PlaceOrderRequest request) {
        return orderService.place(request);
    }

    @GetMapping("/{id}")
    public OrderResponse findById(@PathVariable UUID id) {
        return orderService.findById(id);
    }

    @GetMapping
    public List<OrderResponse> findAll() {
        return orderService.findAll();
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable UUID id,
                                @Valid @RequestBody CancelOrderRequest request) {
        return orderService.cancel(id, request.reason());
    }
}
