package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LikeJpaRepository extends JpaRepository<LikeModel, UUID> {

    Optional<LikeModel> findByUserIdAndProductId(UUID userId, UUID productId);

    boolean existsByUserIdAndProductId(UUID userId, UUID productId);

    Page<LikeModel> findAllByUserId(UUID userId, Pageable pageable);

    void deleteByUserIdAndProductId(UUID userId, UUID productId);
}
