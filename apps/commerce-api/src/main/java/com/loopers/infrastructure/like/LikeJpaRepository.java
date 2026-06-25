package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LikeJpaRepository extends JpaRepository<LikeModel, Long> {
    Optional<LikeModel> findByMemberIdAndProductId(Long memberId, Long productId);
    List<LikeModel> findAllByMemberId(Long memberId);
    List<LikeModel> findAllByMemberIdOrderByCreatedAtDesc(Long memberId);
    void deleteAllByProductId(Long productId);
}
