package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface LikeJpaRepository extends JpaRepository<LikeModel, UUID> {

    Optional<LikeModel> findByUserIdAndProductId(UUID userId, UUID productId);

    boolean existsByUserIdAndProductId(UUID userId, UUID productId);

    Page<LikeModel> findAllByUserId(UUID userId, Pageable pageable);

    /** Product + Brand fetch join — N+1 방지용 페이징 쿼리 (다대일 fetch join이라 페이징 안전) */
    @Query("SELECT l FROM LikeModel l JOIN FETCH l.product p JOIN FETCH p.brand WHERE l.userId = :userId")
    Page<LikeModel> findAllByUserIdWithProduct(@Param("userId") UUID userId, Pageable pageable);

    /** 멱등 삽입 — 유니크 충돌 시 무시(예외 없음). 새로 삽입 1, 중복 0 */
    @Modifying
    @Query(value = "INSERT IGNORE INTO likes (id, user_id, product_id, created_at, updated_at) " +
        "VALUES (:id, :userId, :productId, :now, :now)", nativeQuery = true)
    int insertIgnore(@Param("id") byte[] id, @Param("userId") byte[] userId,
                     @Param("productId") byte[] productId, @Param("now") LocalDateTime now);

    /** bulk DELETE — 실제 삭제된 행 수 반환 (삭제 1, 없음 0) */
    @Modifying
    @Query("DELETE FROM LikeModel l WHERE l.userId = :userId AND l.productId = :productId")
    int deleteByUserIdAndProductIdReturningCount(@Param("userId") UUID userId, @Param("productId") UUID productId);
}
