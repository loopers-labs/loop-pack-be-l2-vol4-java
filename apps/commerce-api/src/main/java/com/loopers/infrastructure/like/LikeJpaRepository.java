package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LikeJpaRepository extends JpaRepository<LikeModel, Long> {

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    List<LikeModel> findByUserId(Long userId);

    Optional<LikeModel> findByUserIdAndProductId(Long userId, Long productId);

    long countByProductId(Long productId);

    /**
     * GROUP BY 로 여러 상품의 좋아요 수를 일괄 집계.
     *
     * @return {@code Object[]{productId(Long), count(Long)}} 리스트
     */
    @Query("SELECT l.productId, COUNT(l) FROM LikeModel l WHERE l.productId IN :productIds GROUP BY l.productId")
    List<Object[]> countGroupedByProductIdIn(@Param("productIds") List<Long> productIds);

    @Modifying
    @Query("DELETE FROM LikeModel l WHERE l.userId = :userId AND l.productId = :productId")
    void deleteByUserIdAndProductId(@Param("userId") Long userId, @Param("productId") Long productId);
}
