package com.loopers.infrastructure.catalog.product;

import com.loopers.domain.catalog.product.ProductStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<ProductJpaEntity, Long> {
    Optional<ProductJpaEntity> findByIdAndStatus(Long id, ProductStatus status);

    List<ProductJpaEntity> findByBrandId(Long brandId);

    List<ProductJpaEntity> findByStatus(ProductStatus status, Pageable pageable);

    List<ProductJpaEntity> findByStatusAndBrandId(ProductStatus status, Long brandId, Pageable pageable);

    long countByStatus(ProductStatus status);

    long countByStatusAndBrandId(ProductStatus status, Long brandId);

    @Query("""
        select p
        from ProductJpaEntity p
        where (:status is null or p.status = :status)
            and (:brandId is null or p.brandId = :brandId)
        """)
    List<ProductJpaEntity> findByOptionalStatusAndBrandId(
        @Param("status") ProductStatus status,
        @Param("brandId") Long brandId,
        Pageable pageable
    );

    @Query("""
        select count(p)
        from ProductJpaEntity p
        where (:status is null or p.status = :status)
            and (:brandId is null or p.brandId = :brandId)
        """)
    long countByOptionalStatusAndBrandId(
        @Param("status") ProductStatus status,
        @Param("brandId") Long brandId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProductJpaEntity p where p.id in :ids order by p.id asc")
    List<ProductJpaEntity> findAllByIdInForUpdate(@Param("ids") Collection<Long> ids);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ProductJpaEntity p set p.likeCount = p.likeCount + 1 where p.id = :productId")
    int increaseLikeCount(@Param("productId") Long productId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ProductJpaEntity p set p.likeCount = p.likeCount - 1 where p.id = :productId and p.likeCount > 0")
    int decreaseLikeCount(@Param("productId") Long productId);
}
