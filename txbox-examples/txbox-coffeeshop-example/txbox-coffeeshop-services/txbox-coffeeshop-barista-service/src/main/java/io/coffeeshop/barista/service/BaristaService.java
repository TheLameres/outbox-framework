package io.coffeeshop.barista.service;

import io.coffeeshop.barista.entity.TicketEntity;
import io.coffeeshop.barista.repository.TicketRepository;
import io.coffeeshop.core.event.BaristaEvents;
import io.coffeeshop.core.event.InventoryEvents;
import io.txbox.producer.starter.template.OutboxTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Приготовление напитка.
 *
 * <p>Реализация намеренно упрощена: тикет создаётся и сразу помечается готовым.
 * Настоящая задержка (реальное время варки) потребовала бы асинхронного
 * исполнителя — это отдельная тема, не относящаяся к паттерну outbox.
 * Для демонстрации саги важна только последовательность событий.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BaristaService {

    private final TicketRepository ticketRepository;
    private final OutboxTemplate outboxTemplate;

    /**
     * Создаёт тикет и публикует {@code barista.drink-prepared} в одной транзакции.
     */
    @Transactional
    public void prepare(InventoryEvents.IngredientsReserved event) {
        UUID orderId = event.orderId();

        // Повторная доставка не должна создать второй тикет и второе событие.
        if (ticketRepository.existsByOrderId(orderId)) {
            log.info("Ticket already exists for order {}, skipping", orderId);
            return;
        }

        Instant now = Instant.now();
        String ticketNo = ticketNo(orderId);

        TicketEntity ticket = TicketEntity.queued(orderId, ticketNo);
        ticket.setStatus(TicketEntity.Status.PREPARED);
        ticket.setStartedAt(now);
        ticket.setPreparedAt(now);
        ticketRepository.save(ticket);

        outboxTemplate.publish(
                new BaristaEvents.DrinkPrepared(orderId, ticketNo, now),
                BaristaEvents.AGGREGATE_TYPE,
                orderId.toString());

        log.info("Drink prepared: order={}, ticket={}", orderId, ticketNo);
    }

    /**
     * Закрывает тикет по отменённому заказу.
     *
     * <p>Событие не публикуется: отмена уже инициирована сервисом заказов,
     * повторное {@code barista.drink-failed} создало бы цикл.
     */
    @Transactional
    public void cancelTicket(UUID orderId, String reason) {
        ticketRepository.findByOrderId(orderId).ifPresent(ticket -> {
            if (ticket.getStatus() == TicketEntity.Status.FAILED) {
                return;
            }
            ticket.setStatus(TicketEntity.Status.FAILED);
            ticket.setFailureReason(reason);
            log.info("Ticket closed for cancelled order {}: {}", orderId, ticketNo(orderId));
        });
    }

    @Transactional(readOnly = true)
    public List<TicketEntity> tickets() {
        return ticketRepository.findAll();
    }

    private static String ticketNo(UUID orderId) {
        return "T-" + String.format("%04d", Math.abs(orderId.hashCode() % 10_000));
    }
}
