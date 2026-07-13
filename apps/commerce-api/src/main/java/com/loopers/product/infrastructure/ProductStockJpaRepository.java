package com.loopers.product.infrastructure;

import com.loopers.product.domain.ProductStock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductStockJpaRepository extends JpaRepository<ProductStock, Long> {

    Optional<ProductStock> findByProductIdAndDeletedAtIsNull(Long productId);

    List<ProductStock> findAllByProductIdInAndDeletedAtIsNull(List<Long> productIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ProductStock s WHERE s.productId = :productId AND s.deletedAt IS NULL")
    Optional<ProductStock> findByProductIdForUpdate(@Param("productId") Long productId);

    /**
     * 재고를 원자적으로 차감한다 — 조회 없이 UPDATE 한 문장으로 재고≥차감량 불변식을 SQL WHERE 로 강제한다.
     * (week7 쿠폰 issued_count < quantity 와 같은 방식.) 조건 불충족이면 0행이 갱신돼 차감이 일어나지 않는다.
     * 벌크 UPDATE 는 @PreUpdate 를 우회하므로 updatedAt 을 명시적으로 갱신한다.
     */
    @Modifying
    @Query("""
        UPDATE ProductStock s
        SET s.quantity = s.quantity - :quantity, s.updatedAt = CURRENT_TIMESTAMP
        WHERE s.productId = :productId AND s.deletedAt IS NULL AND s.quantity >= :quantity
        """)
    int decreaseStock(@Param("productId") Long productId, @Param("quantity") int quantity);

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE ProductStock s
        SET s.deletedAt = CURRENT_TIMESTAMP, s.updatedAt = CURRENT_TIMESTAMP
        WHERE s.productId IN (SELECT p.id FROM Product p WHERE p.brandId = :brandId)
          AND s.deletedAt IS NULL
        """)
    int softDeleteByBrandId(@Param("brandId") Long brandId);
}
