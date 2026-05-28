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

public interface LikeJpaRepository extends JpaRepository<LikeJpaEntity, Long> {

    Optional<LikeJpaEntity> findByUserIdAndProductIdAndDeletedAtIsNull(Long userId, Long productId);

    Optional<LikeJpaEntity> findByUserIdAndProductId(Long userId, Long productId);

    Page<LikeJpaEntity> findAllByUserIdAndDeletedAtIsNull(Long userId, Pageable pageable);

    @Modifying
    @Query("UPDATE LikeJpaEntity l SET l.deletedAt = :now WHERE l.productId = :productId AND l.deletedAt IS NULL")
    void softDeleteAllByProductId(@Param("productId") Long productId, @Param("now") ZonedDateTime now);

    @Modifying
    @Query("UPDATE LikeJpaEntity l SET l.deletedAt = :now WHERE l.productId IN :productIds AND l.deletedAt IS NULL")
    void softDeleteAllByProductIds(@Param("productIds") List<Long> productIds, @Param("now") ZonedDateTime now);
}
