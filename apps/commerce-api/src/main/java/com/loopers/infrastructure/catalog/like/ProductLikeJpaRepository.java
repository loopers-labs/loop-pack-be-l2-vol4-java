package com.loopers.infrastructure.catalog.like;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ProductLikeJpaRepository extends JpaRepository<ProductLikeJpaEntity, Long> {
    Optional<ProductLikeJpaEntity> findByUserIdAndProductId(String userId, Long productId);

    boolean existsByUserIdAndProductId(String userId, Long productId);

    List<ProductLikeJpaEntity> findByUserId(String userId, Pageable pageable);

    @Query("select pl.productId from ProductLikeJpaEntity pl where pl.userId = :userId and pl.productId in :productIds")
    Set<Long> findLikedProductIds(@Param("userId") String userId, @Param("productIds") Collection<Long> productIds);

    long countByUserId(String userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = """
            insert ignore into product_like (user_id, product_id, created_at, updated_at)
            values (:userId, :productId, utc_timestamp(6), utc_timestamp(6))
            """,
        nativeQuery = true
    )
    int insertIgnore(@Param("userId") String userId, @Param("productId") Long productId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ProductLikeJpaEntity pl where pl.userId = :userId and pl.productId = :productId")
    int deleteByUserIdAndProductId(@Param("userId") String userId, @Param("productId") Long productId);
}
