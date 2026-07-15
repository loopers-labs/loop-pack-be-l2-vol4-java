package com.loopers.infrastructure.like;

import com.loopers.domain.like.ProductLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductLikeJpaRepository extends JpaRepository<ProductLike, Long> {

    /**
     * INSERT IGNORE 로 유니크 제약 충돌 시 예외 대신 영향 행수 0을 반환한다. 예외 기반 처리(트랜잭션 rollback-only
     * 오염) 없이 "새로 등록된 경우에만 카운트 증가"를 단일 문장으로 보장한다.
     */
    @Modifying
    @Query(
        value =
            "INSERT IGNORE INTO product_like (user_id, product_id, created_at, updated_at)"
                + " VALUES (:userId, :productId, NOW(), NOW())",
        nativeQuery = true)
    int insertIgnore(@Param("userId") String userId, @Param("productId") Long productId);

    @Modifying
    @Query("DELETE FROM ProductLike pl WHERE pl.userId = :userId AND pl.productId = :productId")
    int deleteByUserIdAndProductId(@Param("userId") String userId, @Param("productId") Long productId);
}
