package io.coffeeshop.inventory.service;

import io.coffeeshop.core.domain.IngredientRequirement;
import io.coffeeshop.core.domain.InsufficientStockException;
import io.coffeeshop.core.domain.StockLevel;
import io.coffeeshop.core.event.InventoryEvents;
import io.coffeeshop.core.event.OrderEvents;
import io.coffeeshop.inventory.entity.ReservationEntity;
import io.coffeeshop.inventory.entity.StockLevelEntity;
import io.coffeeshop.inventory.repository.ReservationRepository;
import io.coffeeshop.inventory.repository.StockLevelRepository;
import io.txbox.producer.starter.template.OutboxTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Резервирование ингредиентов.
 *
 * <p>Резерв атомарен по заказу: либо зарезервированы все ингредиенты, либо ни одного.
 * Частичный резерв оставил бы склад в состоянии, из которого сага не может выйти —
 * заказ не подтверждён, но ингредиенты уже заняты.
 *
 * <p>Порядок блокировок детерминирован: ингредиенты обходятся по алфавиту кода.
 * Без этого два параллельных заказа с пересекающимся составом могли бы взять
 * строки в разном порядке и получить дедлок. Классификатор превратил бы его
 * в retry, но это лишняя работа и шум в логах.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final StockLevelRepository stockLevelRepository;
    private final ReservationRepository reservationRepository;
    private final OutboxTemplate outboxTemplate;

    /**
     * Резервирует ингредиенты под заказ.
     *
     * <p>Публикует {@code inventory.reserved} либо {@code inventory.rejected}
     * в той же транзакции, что и изменение остатков.
     */
    @Transactional
    public void reserve(OrderEvents.OrderPlaced event) {
        UUID orderId = event.orderId();
        Map<String, BigDecimal> required = aggregate(event);

        if (required.isEmpty()) {
            log.warn("order.placed {} carries no ingredient requirements", orderId);
            publishReserved(orderId, List.of());
            return;
        }

        // Идемпотентность на уровне бизнеса: повторная доставка события не должна
        // резервировать второй раз. Дедупликация inbox ловит повторы по messageId,
        // это независимая линия защиты — на случай перепубликации с новым id.
        if (reservationRepository.existsByOrderIdAndIngredientCode(
                orderId, required.keySet().iterator().next())) {
            log.info("Reservation already exists for order {}, skipping", orderId);
            return;
        }

        // Проход 1: блокируем строки и проверяем достаточность. Ничего не меняем,
        // поэтому при нехватке откатывать нечего — второй проход просто не начнётся.
        Map<String, StockLevelEntity> locked = new LinkedHashMap<>();
        List<InventoryEvents.IngredientsRejected.Missing> missing = new ArrayList<>();

        for (String code : new TreeSet<>(required.keySet())) {
            BigDecimal amount = required.get(code);
            Optional<StockLevelEntity> found = stockLevelRepository.findForUpdate(code);

            if (found.isEmpty()) {
                missing.add(new InventoryEvents.IngredientsRejected.Missing(
                        code, amount, BigDecimal.ZERO, null));
                continue;
            }

            StockLevel level = found.get().toDomain();
            if (level.available().compareTo(amount) < 0) {
                missing.add(new InventoryEvents.IngredientsRejected.Missing(
                        code, amount, level.available(), level.unit()));
                continue;
            }
            locked.put(code, found.get());
        }

        if (!missing.isEmpty()) {
            outboxTemplate.publish(
                    new InventoryEvents.IngredientsRejected(orderId, missing, Instant.now()),
                    InventoryEvents.AGGREGATE_TYPE,
                    orderId.toString());
            log.warn("Reservation rejected for order {}: {} ingredient(s) missing",
                    orderId, missing.size());
            return;
        }

        // Проход 2: применяем. Строки уже под FOR UPDATE из прохода 1,
        // поэтому между проверкой и записью никто не вклинится.
        List<InventoryEvents.IngredientsReserved.Item> items = new ArrayList<>();
        locked.forEach((code, entity) -> {
            BigDecimal amount = required.get(code);
            StockLevel next;
            try {
                next = entity.toDomain().reserve(amount);
            } catch (InsufficientStockException e) {
                // Недостижимо после проверки в проходе 1, но пусть падает громко,
                // а не тихо оставляет склад в неучтённом состоянии.
                throw new IllegalStateException(
                        "stock changed between check and apply for " + code, e);
            }
            entity.apply(next);

            reservationRepository.save(ReservationEntity.reserve(
                    orderId, code, amount, next.unit()));
            items.add(new InventoryEvents.IngredientsReserved.Item(code, amount, next.unit()));
        });

        publishReserved(orderId, items);
        log.info("Reserved {} ingredient(s) for order {}", items.size(), orderId);
    }

    /**
     * Освобождает резерв — компенсация после отмены заказа.
     *
     * <p>Освобождаются только резервы в статусе {@code RESERVED}: списанные
     * ингредиенты физически израсходованы, возвращать их на склад нельзя.
     */
    @Transactional
    public void release(OrderEvents.OrderCancelled event) {
        UUID orderId = event.orderId();

        List<ReservationEntity> active = reservationRepository.findByOrderId(orderId).stream()
                .filter(r -> r.getStatus() == ReservationEntity.Status.RESERVED)
                .toList();

        if (active.isEmpty()) {
            // Резерва не было (отказ по нехватке) или он уже снят — повторная доставка.
            log.info("Nothing to release for order {}", orderId);
            return;
        }

        for (ReservationEntity reservation : active) {
            stockLevelRepository.findForUpdate(reservation.getIngredientCode())
                    .ifPresentOrElse(
                            entity -> entity.apply(
                                    entity.toDomain().release(reservation.getAmount())),
                            () -> log.warn("Stock level vanished for {}",
                                    reservation.getIngredientCode()));
            reservation.setStatus(ReservationEntity.Status.RELEASED);
        }

        outboxTemplate.publish(
                new InventoryEvents.IngredientsReleased(orderId, Instant.now()),
                InventoryEvents.AGGREGATE_TYPE,
                orderId.toString());

        log.info("Released {} reservation(s) for order {}", active.size(), orderId);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void publishReserved(UUID orderId, List<InventoryEvents.IngredientsReserved.Item> items) {
        outboxTemplate.publish(
                new InventoryEvents.IngredientsReserved(orderId, items, Instant.now()),
                InventoryEvents.AGGREGATE_TYPE,
                orderId.toString());
    }

    /**
     * Сводит состав всех строк в единый список: {@code amount * quantity}.
     *
     * <p>Один ингредиент может встречаться в нескольких позициях заказа —
     * суммируем, иначе второе требование перезаписало бы первое.
     */
    private static Map<String, BigDecimal> aggregate(OrderEvents.OrderPlaced event) {
        return event.lines().stream()
                .flatMap(line -> line.totalRequirements().stream())
                .collect(Collectors.toMap(
                        IngredientRequirement::ingredientCode,
                        IngredientRequirement::amount,
                        BigDecimal::add,
                        TreeMap::new));
    }
}
