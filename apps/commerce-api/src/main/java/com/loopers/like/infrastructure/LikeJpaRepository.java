package com.loopers.like.infrastructure;

import com.loopers.like.domain.Like;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LikeJpaRepository extends JpaRepository<Like, Long> {
    Optional<Like> findByUserIdAndProductId(Long userId, Long productId);
    List<Like> findAllByUserIdAndDeletedAtIsNull(Long userId);
    long countByProductIdAndDeletedAtIsNull(Long productId);

    @Query(value = """
        SELECT l.product_id AS productId, COUNT(*) AS likeCount
        FROM likes l
        WHERE l.product_id IN :productIds AND l.deleted_at IS NULL
        GROUP BY l.product_id
        """, nativeQuery = true)
    List<LikeCountProjection> countActiveByProductIds(@Param("productIds") List<Long> productIds);

    interface LikeCountProjection {
        Long getProductId();
        long getLikeCount();
    }
}
