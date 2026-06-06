package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductStockModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductStockJpaRepository extends JpaRepository<ProductStockModel, Long> {

    @Query("SELECT s FROM ProductStockModel s WHERE s.product.id = :productId")
    List<ProductStockModel> findAllByProductId(@Param("productId") Long productId);
}
