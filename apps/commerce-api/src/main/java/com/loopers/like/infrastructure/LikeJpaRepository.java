package com.loopers.like.infrastructure;

import com.loopers.like.domain.Like;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LikeJpaRepository extends JpaRepository<Like, Long> {
    boolean existsByMemberIdAndProductId(Long memberId, Long productId);

    Optional<Like> findByMemberIdAndProductId(Long memberId, Long productId);

    long countByProductId(Long productId);

    List<Like> findByMemberId(Long memberId);
}
