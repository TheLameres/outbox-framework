package io.coffeeshop.inventory.web;

import io.coffeeshop.inventory.repository.StockLevelRepository;
import io.coffeeshop.inventory.web.dto.StockResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Диагностика склада.
 *
 * <p>Только чтение: остатки меняются исключительно через события, чтобы
 * не завести второй путь записи в обход саги.
 */
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final StockLevelRepository stockLevelRepository;

    @GetMapping("/stock")
    public List<StockResponse> stock() {
        return stockLevelRepository.findAll().stream()
                .map(StockResponse::from)
                .toList();
    }
}
