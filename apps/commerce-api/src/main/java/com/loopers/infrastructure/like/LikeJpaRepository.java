package com.loopers.infrastructure.like;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LikeJpaRepository extends JpaRepository<LikeEntity, Long> {
    Optional<LikeEntity> findByUserIdAndProductId(Long userId, Long productId);

    List<LikeEntity> findByProductIdAndDeletedAtIsNull(Long productId);

    List<LikeEntity> findByUserIdAndDeletedAtIsNullOrderByLikedAtDescIdDesc(Long userId, Pageable pageable);

    @Query("select l.productId from LikeEntity l " +
           "where l.userId = :userId and l.productId in :productIds and l.deletedAt is null")
    List<Long> findLikedProductIds(@Param("userId") Long userId, @Param("productIds") Collection<Long> productIds);
}
