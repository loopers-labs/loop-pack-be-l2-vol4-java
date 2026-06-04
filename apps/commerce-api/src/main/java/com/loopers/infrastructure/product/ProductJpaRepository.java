package com.loopers.infrastructure.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<ProductJpaEntity, Long> {

    Optional<ProductJpaEntity> findByIdAndDeletedAtIsNull(Long id);

    List<ProductJpaEntity> findAllByIdInAndDeletedAtIsNull(Collection<Long> ids);

    Page<ProductJpaEntity> findAllByDeletedAtIsNull(Pageable pageable);

    Page<ProductJpaEntity> findAllByBrandIdAndDeletedAtIsNull(Long brandId, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE ProductJpaEntity p
        SET p.deletedAt = CURRENT_TIMESTAMP,
            p.updatedAt = CURRENT_TIMESTAMP
        WHERE p.brandId = :brandId AND p.deletedAt IS NULL
    """)
    int bulkSoftDeleteByBrandId(Long brandId);

    @Query("""
        SELECT p
        FROM ProductJpaEntity p
        LEFT JOIN LikeJpaEntity l ON l.productId = p.id
        WHERE p.deletedAt IS NULL
        GROUP BY p
        ORDER BY COUNT(l) DESC, p.id DESC
    """)
    Page<ProductJpaEntity> findAllOrderByLikesDesc(Pageable pageable);

    @Query("""
        SELECT p
        FROM ProductJpaEntity p
        LEFT JOIN LikeJpaEntity l ON l.productId = p.id
        WHERE p.deletedAt IS NULL AND p.brandId = :brandId
        GROUP BY p
        ORDER BY COUNT(l) DESC, p.id DESC
    """)
    Page<ProductJpaEntity> findAllByBrandIdOrderByLikesDesc(Long brandId, Pageable pageable);
}
