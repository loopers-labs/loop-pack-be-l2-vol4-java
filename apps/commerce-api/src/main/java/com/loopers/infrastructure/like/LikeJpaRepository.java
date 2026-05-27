package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LikeJpaRepository extends JpaRepository<LikeModel, Long> {
    boolean existsByUserIdAndProductId(Long userId, Long productId);
    void deleteByUserIdAndProductId(Long userId, Long productId);
    List<LikeModel> findAllByUserId(Long userId);
    long countByProductId(Long productId);

    @Query("SELECT l.productId, COUNT(l) FROM LikeModel l WHERE l.productId IN :productIds GROUP BY l.productId")
    List<Object[]> countGroupByProductIdIn(@Param("productIds") List<Long> productIds);
}
