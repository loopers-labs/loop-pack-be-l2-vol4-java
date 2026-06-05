package com.loopers.infrastructure.like;

import com.loopers.domain.like.Like;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface LikeJpaRepository extends JpaRepository<Like, Like.LikeId> {

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    void deleteByUserIdAndProductId(Long userId, Long productId);

    long countByProductId(Long productId);

    Page<Like> findAllByUserId(Long userId, Pageable pageable);

    @Modifying
    @Query(value = """
        INSERT IGNORE INTO product_likes (user_id, product_id, created_at)
        VALUES (:userId, :productId, NOW(6))
    """, nativeQuery = true)
    int insertIgnore(@Param("userId") Long userId, @Param("productId") Long productId);

    @Query("""
        SELECT l.productId AS productId, COUNT(l) AS count
        FROM ProductLike l
        WHERE l.productId IN :productIds
        GROUP BY l.productId
    """)
    List<LikeCountRow> findCountsByProductIds(@Param("productIds") Collection<Long> productIds);

    interface LikeCountRow {
        Long getProductId();
        Long getCount();
    }
}
