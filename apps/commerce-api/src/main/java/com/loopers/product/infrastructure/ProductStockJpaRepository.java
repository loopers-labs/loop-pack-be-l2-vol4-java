package com.loopers.product.infrastructure;

import com.loopers.product.domain.ProductStock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductStockJpaRepository extends JpaRepository<ProductStock, Long> {

    Optional<ProductStock> findByProductIdAndDeletedAtIsNull(Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ProductStock s WHERE s.productId = :productId AND s.deletedAt IS NULL")
    Optional<ProductStock> findByProductIdForUpdate(@Param("productId") Long productId);

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE ProductStock s
        SET s.deletedAt = CURRENT_TIMESTAMP, s.updatedAt = CURRENT_TIMESTAMP
        WHERE s.productId IN (SELECT p.id FROM Product p WHERE p.brandId = :brandId)
          AND s.deletedAt IS NULL
        """)
    int softDeleteByBrandId(@Param("brandId") Long brandId);
}
