package com.loopers.infrastructure.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, Long> {

    Optional<ProductEntity> findByIdAndDeletedAtIsNull(Long id);

    Page<ProductEntity> findAllByDeletedAtIsNull(Pageable pageable);

    Page<ProductEntity> findAllByBrandIdAndDeletedAtIsNull(Long brandId, Pageable pageable);

    @Transactional
    @Modifying
    @Query("UPDATE Product p SET p.deletedAt = CURRENT_TIMESTAMP WHERE p.brandId = :brandId AND p.deletedAt IS NULL")
    void deleteAllByBrandId(@Param("brandId") Long brandId);

    @Transactional
    @Modifying
    @Query("UPDATE Product p SET p.likeCount = p.likeCount + 1 WHERE p.id = :id")
    void incrementLikeCount(@Param("id") Long id);

    @Transactional
    @Modifying
    @Query("UPDATE Product p SET p.likeCount = p.likeCount - 1 WHERE p.id = :id AND p.likeCount > 0")
    void decrementLikeCount(@Param("id") Long id);

    @Transactional
    @Modifying
    @Query(value = "UPDATE product SET like_count = GREATEST(0, like_count + :amount) WHERE id = :id", nativeQuery = true)
    void adjustLikeCount(@Param("id") Long id, @Param("amount") long amount);
}
