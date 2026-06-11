package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LikeJpaRepository extends JpaRepository<LikeModel, Long> {

    Optional<LikeModel> findByUserIdAndProductId(Long userId, Long productId);

    List<LikeModel> findAllByUserId(Long userId);

    // MySQL INSERT IGNORE: uk_likes_user_product 중복 시 예외 없이 0 반환, 삽입 성공 시 1 반환
    // 대상 DB: MySQL 8.0
    @Modifying
    @Query(value = "INSERT IGNORE INTO likes (user_id, product_id, created_at, updated_at) VALUES (:userId, :productId, NOW(6), NOW(6))", nativeQuery = true)
    int insertIgnore(@Param("userId") Long userId, @Param("productId") Long productId);

    @Modifying
    @Query("DELETE FROM LikeModel l WHERE l.userId = :userId AND l.productId = :productId")
    int deleteByUserIdAndProductId(@Param("userId") Long userId, @Param("productId") Long productId);
}
