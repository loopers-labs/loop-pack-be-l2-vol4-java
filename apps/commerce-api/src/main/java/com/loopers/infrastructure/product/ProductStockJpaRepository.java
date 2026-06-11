package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductStockModel;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductStockJpaRepository extends JpaRepository<ProductStockModel, Long> {

    Optional<ProductStockModel> findByProductIdAndDeletedAtIsNull(Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ProductStockModel s WHERE s.productId = :productId AND s.deletedAt IS NULL")
    Optional<ProductStockModel> findByProductIdForUpdate(@Param("productId") Long productId);
}