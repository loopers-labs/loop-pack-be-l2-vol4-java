package com.loopers.infrastructure.like;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface LikeJpaRepository extends JpaRepository<LikeJpaEntity, String> {

    Optional<LikeJpaEntity> findByUserIdAndProductIdAndDeletedAtIsNull(String userId, String productId);

    Optional<LikeJpaEntity> findByUserIdAndProductId(String userId, String productId);

    Page<LikeJpaEntity> findAllByUserIdAndDeletedAtIsNull(String userId, Pageable pageable);

    @Modifying
    @Query("UPDATE LikeJpaEntity l SET l.deletedAt = :now WHERE l.productId = :productId AND l.deletedAt IS NULL")
    void softDeleteAllByProductId(@Param("productId") String productId, @Param("now") ZonedDateTime now);

    @Modifying
    @Query("UPDATE LikeJpaEntity l SET l.deletedAt = :now WHERE l.productId IN :productIds AND l.deletedAt IS NULL")
    void softDeleteAllByProductIds(@Param("productIds") List<String> productIds, @Param("now") ZonedDateTime now);
}
