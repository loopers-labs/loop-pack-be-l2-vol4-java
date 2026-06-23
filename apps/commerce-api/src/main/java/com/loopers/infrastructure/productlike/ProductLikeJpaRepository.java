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
