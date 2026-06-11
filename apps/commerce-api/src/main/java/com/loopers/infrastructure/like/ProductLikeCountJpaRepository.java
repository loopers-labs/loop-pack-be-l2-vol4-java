package com.loopers.infrastructure.like;

import com.loopers.domain.like.ProductLikeCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductLikeCountJpaRepository extends JpaRepository<ProductLikeCount, Long> {

    Optional<ProductLikeCount> findByProductId(Long productId);

    List<ProductLikeCount> findByProductIdIn(List<Long> productIds);

    // upsert: 행이 없으면 1로 생성, 있으면 +1 — 검증과 갱신을 한 문장으로 묶어 race window 제거
    @Modifying
    @Query(value = """
        INSERT INTO product_like_count (product_id, like_count, created_at, updated_at)
        VALUES (:productId, 1, NOW(), NOW())
        ON DUPLICATE KEY UPDATE like_count = like_count + 1, updated_at = NOW()
        """, nativeQuery = true)
    void increase(@Param("productId") Long productId);

    @Modifying
    @Query(value = """
        UPDATE product_like_count
        SET like_count = like_count - 1, updated_at = NOW()
        WHERE product_id = :productId AND like_count > 0
        """, nativeQuery = true)
    void decrease(@Param("productId") Long productId);
}
