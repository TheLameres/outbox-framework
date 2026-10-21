package io.coffeeshop.inventory.repository;

import io.coffeeshop.inventory.entity.StockLevelEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StockLevelRepository extends JpaRepository<StockLevelEntity, String> {

    /**
     * Читает остаток с пессимистичной блокировкой.
     *
     * <p>Резервирование — операция «прочитал-изменил-записал»: два параллельных
     * заказа на один и тот же ингредиент без блокировки спишут его дважды.
     * {@code SELECT ... FOR UPDATE} сериализует их на уровне строки.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StockLevelEntity s WHERE s.ingredientCode = :code")
    Optional<StockLevelEntity> findForUpdate(@Param("code") String code);
}
