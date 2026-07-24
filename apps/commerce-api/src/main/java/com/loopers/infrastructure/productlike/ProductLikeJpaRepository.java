package com.loopers.infrastructure.productlike;

import com.loopers.domain.productlike.ProductLikeModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductLikeJpaRepository extends JpaRepository<ProductLikeModel, Long> {

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    /**
     * (userId, productId) 좋아요를 멱등하게 insert한다. unique 제약(uk_product_like_user_product)에 걸리면
     * MySQL이 예외 없이 무시하고 0행을 반환한다. created_at/updated_at은 NOT NULL이고 DB 기본값이 없어
     * NOW()로 채운다(네이티브 insert는 @PrePersist를 타지 않으므로).
     *
     * @return 새로 insert되면 1, 이미 존재하면 0
     */
    @Modifying
    @Query(value = "INSERT IGNORE INTO product_like (user_id, product_id, created_at, updated_at) "
        + "VALUES (:userId, :productId, NOW(), NOW())", nativeQuery = true)
    int insertIgnore(@Param("userId") Long userId, @Param("productId") Long productId);

    /**
     * (userId, productId) 좋아요를 삭제하고 영향받은 행 수를 반환한다.
     * 멱등 취소 판단(실제로 삭제가 일어났는지)에 사용한다.
     *
     * @return 삭제된 행 수 (1이면 취소 성공, 0이면 좋아요 상태가 아니었음)
     */
    @Modifying
    @Query("DELETE FROM ProductLikeModel l WHERE l.userId = :userId AND l.productId = :productId")
    int deleteByUserIdAndProductId(@Param("userId") Long userId, @Param("productId") Long productId);

    @Query("SELECT l.productId FROM ProductLikeModel l WHERE l.userId = :userId ORDER BY l.id DESC")
    List<Long> findProductIdsByUserId(@Param("userId") Long userId);
}
