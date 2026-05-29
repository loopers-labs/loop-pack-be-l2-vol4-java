package com.loopers.infrastructure.like;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LikeJpaRepository extends JpaRepository<LikeEntity, Long> {
    Optional<LikeEntity> findByUserIdAndProductId(Long userId, Long productId);
    boolean existsByUserIdAndProductId(Long userId, Long productId);
    List<LikeEntity> findAllByUserId(Long userId);
    long countByProductId(Long productId);

    @Query("SELECT l.productId, COUNT(l) FROM LikeEntity l WHERE l.productId IN :productIds GROUP BY l.productId")
    List<Object[]> countGroupedByProductIds(@Param("productIds") Collection<Long> productIds);
}
