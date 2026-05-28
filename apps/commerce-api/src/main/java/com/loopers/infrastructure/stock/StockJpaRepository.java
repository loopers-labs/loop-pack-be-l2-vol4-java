package com.loopers.infrastructure.stock;

import com.loopers.domain.stock.StockModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface StockJpaRepository extends JpaRepository<StockModel, UUID> {

    Optional<StockModel> findByProductId(UUID productId);

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE StockModel s
        SET s.reservedQuantity = s.reservedQuantity + :qty
        WHERE s.productId = :productId
          AND (s.totalQuantity - s.reservedQuantity) >= :qty
        """)
    int reserve(@Param("productId") UUID productId, @Param("qty") int qty);

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE StockModel s
        SET s.totalQuantity = :newTotal
        WHERE s.productId = :productId
          AND :newTotal >= s.reservedQuantity
        """)
    int updateTotal(@Param("productId") UUID productId, @Param("newTotal") int newTotal);
}
