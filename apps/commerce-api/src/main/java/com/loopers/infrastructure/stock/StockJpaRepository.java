package com.loopers.infrastructure.stock;

import com.loopers.domain.stock.StockModel;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StockJpaRepository extends JpaRepository<StockModel, Long> {
    Optional<StockModel> findByProductId(Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StockModel s WHERE s.productId = :productId AND s.deletedAt IS NULL")
    Optional<StockModel> findByProductIdForUpdate(@Param("productId") Long productId);
}
