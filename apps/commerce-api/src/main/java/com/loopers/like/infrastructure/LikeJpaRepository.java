package com.loopers.like.infrastructure;

import com.loopers.like.domain.LikeModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LikeJpaRepository extends JpaRepository<LikeModel, Long> {
    Optional<LikeModel> findByUserIdAndProductId(Long userId, Long productId);
    List<LikeModel> findAllByUserId(Long userId);
}
