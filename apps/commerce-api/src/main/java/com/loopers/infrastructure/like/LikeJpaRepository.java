package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface LikeJpaRepository extends JpaRepository<LikeModel, UUID> {

    Optional<LikeModel> findByUserIdAndProductId(UUID userId, UUID productId);

    boolean existsByUserIdAndProductId(UUID userId, UUID productId);

    Page<LikeModel> findAllByUserId(UUID userId, Pageable pageable);

    /** Product + Brand fetch join — N+1 방지용 페이징 쿼리 (다대일 fetch join이라 페이징 안전) */
    @Query("SELECT l FROM LikeModel l JOIN FETCH l.product p JOIN FETCH p.brand WHERE l.userId = :userId")
    Page<LikeModel> findAllByUserIdWithProduct(@Param("userId") UUID userId, Pageable pageable);

    void deleteByUserIdAndProductId(UUID userId, UUID productId);
}
