package io.coffeeshop.barista.web;

import io.coffeeshop.barista.entity.TicketEntity;
import io.coffeeshop.barista.service.BaristaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Диагностика стойки: список тикетов.
 */
@RestController
@RequestMapping("/api/barista")
@RequiredArgsConstructor
public class BaristaController {

    private final BaristaService baristaService;

    public record TicketResponse(UUID id, UUID orderId, String ticketNo,
                                 String status, Instant preparedAt) {

        static TicketResponse from(TicketEntity t) {
            return new TicketResponse(t.getId(), t.getOrderId(), t.getTicketNo(),
                    t.getStatus().name(), t.getPreparedAt());
        }
    }

    @GetMapping("/tickets")
    public List<TicketResponse> tickets() {
        return baristaService.tickets().stream().map(TicketResponse::from).toList();
    }
}
