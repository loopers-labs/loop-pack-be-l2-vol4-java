package com.loopers.like.infrastructure;

import com.loopers.like.domain.Like;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LikeJpaRepository extends JpaRepository<Like, Long> {
    interface LikeCountProjection {
        Long getProductId();

        Long getLikeCount();
    }

    @Modifying
    @Query(
        value =
            "INSERT IGNORE INTO product_like (member_id, product_id, created_at, updated_at)"
                + " VALUES (:memberId, :productId, NOW(), NOW())",
        nativeQuery = true)
    int insertIgnore(@Param("memberId") Long memberId, @Param("productId") Long productId);

    boolean existsByMemberIdAndProductId(Long memberId, Long productId);

    Optional<Like> findByMemberIdAndProductId(Long memberId, Long productId);

    long countByProductId(Long productId);

    @Query(
        value =
            "SELECT product_id AS productId, COUNT(*) AS likeCount"
                + " FROM product_like WHERE product_id IN (:productIds) GROUP BY product_id",
        nativeQuery = true)
    List<LikeCountProjection> countByProductIds(
        @Param("productIds") Collection<Long> productIds);

    List<Like> findByMemberId(Long memberId);
}
