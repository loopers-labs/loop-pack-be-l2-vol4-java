package com.loopers.stock.infrastructure;

import com.loopers.stock.domain.StockModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockJpaRepository extends JpaRepository<StockModel, Long> {
    Optional<StockModel> findByProductId(Long productId);

    @Query("SELECT s FROM StockModel s WHERE s.productId IN :productIds")
    List<StockModel> findAllByProductIds(@Param("productIds") List<Long> productIds);
}
