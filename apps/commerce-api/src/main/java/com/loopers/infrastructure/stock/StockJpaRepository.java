package com.loopers.infrastructure.stock;

import com.loopers.domain.stock.StockModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockJpaRepository extends JpaRepository<StockModel, Long> {
    Optional<StockModel> findByProductId(Long productId);

    List<StockModel> findAllByProductIdIn(Collection<Long> productIds);

    @Modifying
    @Query("UPDATE StockModel s SET s.quantity = s.quantity - :quantity WHERE s.productId = :productId AND s.quantity >= :quantity")
    int decreaseQuantity(@Param("productId") Long productId, @Param("quantity") Long quantity);

    @Modifying
    @Query("UPDATE StockModel s SET s.deletedAt = :deletedAt WHERE s.productId IN :productIds AND s.deletedAt IS NULL")
    void softDeleteAllByProductIdIn(@Param("productIds") Collection<Long> productIds, @Param("deletedAt") ZonedDateTime deletedAt);
}
