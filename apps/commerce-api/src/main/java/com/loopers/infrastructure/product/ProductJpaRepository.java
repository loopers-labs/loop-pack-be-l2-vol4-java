package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.enums.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductJpaRepository extends JpaRepository<ProductModel, Long> {

    List<ProductModel> findAllByBrandId(Long brandId);

    @Modifying
    @Query("UPDATE ProductModel p SET p.status = :status WHERE p.brandId = :brandId")
    void updateStatusByBrandId(@Param("brandId") Long brandId, @Param("status") ProductStatus status);
}
