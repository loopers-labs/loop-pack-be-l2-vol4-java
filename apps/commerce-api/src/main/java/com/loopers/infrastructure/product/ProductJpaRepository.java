package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductJpaRepository extends JpaRepository<ProductModel, Long> {

    @Query("SELECT p FROM ProductModel p WHERE (:brandId IS NULL OR p.brandId = :brandId)")
    Page<ProductModel> findAllByBrandId(@Param("brandId") Long brandId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductModel p WHERE p.id IN :ids ORDER BY p.id ASC")
    List<ProductModel> findAllByIdsWithLock(@Param("ids") List<Long> ids);

    @Query("SELECT p.id FROM ProductModel p WHERE p.brandId = :brandId")
    List<Long> findIdsByBrandId(@Param("brandId") Long brandId);

    @Modifying
    @Query(value = "UPDATE product SET deleted_at = NOW() WHERE brand_id = :brandId AND deleted_at IS NULL", nativeQuery = true)
    void bulkSoftDeleteByBrandId(@Param("brandId") Long brandId);
}
