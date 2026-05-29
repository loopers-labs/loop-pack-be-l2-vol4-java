package com.loopers.infrastructure.product;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, Long> {

    @Query("SELECT p FROM ProductEntity p WHERE (:brandId IS NULL OR p.brand.id = :brandId)")
    Page<ProductEntity> findAllByBrandId(@Param("brandId") Long brandId, Pageable pageable);

    @Query("SELECT p FROM ProductEntity p WHERE (:brandId IS NULL OR p.brand.id = :brandId) ORDER BY (SELECT COUNT(l) FROM LikeEntity l WHERE l.productId = p.id) DESC")
    Page<ProductEntity> findAllOrderByLikeCountDesc(@Param("brandId") Long brandId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductEntity p WHERE p.id = :id")
    Optional<ProductEntity> findByIdWithLock(@Param("id") Long id);

    @Modifying
    @Query("DELETE FROM ProductEntity p WHERE p.brand.id = :brandId")
    void deleteByBrandId(@Param("brandId") Long brandId);
}
