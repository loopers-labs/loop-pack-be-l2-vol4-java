package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LikeJpaRepository extends JpaRepository<LikeModel, Long> {

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    @Modifying
    @Query("delete from LikeModel l where l.userId = :userId and l.productId = :productId")
    int deleteByUserIdAndProductId(@Param("userId") Long userId, @Param("productId") Long productId);
}
