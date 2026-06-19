package com.loopers.infrastructure.stock;

import com.loopers.domain.stock.StockModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockJpaRepository extends JpaRepository<StockModel, Long> {
    Optional<StockModel> findByProduct_Id(Long productId);
    List<StockModel> findAllByProduct_IdIn(List<Long> productIds);

    @Modifying
    @Query("UPDATE StockModel s SET s.quantity = s.quantity - :qty WHERE s.product.id = :productId AND s.quantity >= :qty AND :qty > 0")
    int decreaseQuantity(@Param("productId") Long productId, @Param("qty") int qty);
}
