package com.loopers.infrastructure.like;

import com.loopers.domain.like.Like;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LikeJpaRepository extends JpaRepository<Like, Like.LikeId> {

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    int deleteByUserIdAndProductId(Long userId, Long productId);

    long countByProductId(Long productId);

    Page<Like> findAllByUserId(Long userId, Pageable pageable);

    @Modifying
    @Query(value = """
        INSERT IGNORE INTO product_likes (user_id, product_id, created_at)
        VALUES (:userId, :productId, NOW(6))
    """, nativeQuery = true)
    int insertIgnore(@Param("userId") Long userId, @Param("productId") Long productId);
}
