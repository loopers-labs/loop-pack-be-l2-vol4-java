package com.loopers.infrastructure.product;

import com.loopers.domain.product.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {

    @Query("SELECT p FROM Product p WHERE (:brandId IS NULL OR p.brandId = :brandId)")
    Page<Product> findAllByBrandIdFilter(@Param("brandId") Long brandId, Pageable pageable);

    @Query("SELECT p.id FROM Product p WHERE p.brandId = :brandId")
    List<Long> findIdsByBrandId(@Param("brandId") Long brandId);

    @Modifying
    @Query("UPDATE Product p SET p.deletedAt = :now WHERE p.brandId = :brandId AND p.deletedAt IS NULL")
    int softDeleteAllByBrandId(@Param("brandId") Long brandId, @Param("now") ZonedDateTime now);
}
