package com.loopers.like.infrastructure;

import com.loopers.like.domain.LikeModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LikeJpaRepository extends JpaRepository<LikeModel, Long> {
    boolean existsByMemberIdAndProductId(Long memberId, Long productId);

    Optional<LikeModel> findByMemberIdAndProductId(Long memberId, Long productId);

    long countByProductId(Long productId);

    List<LikeModel> findByMemberId(Long memberId);
}
