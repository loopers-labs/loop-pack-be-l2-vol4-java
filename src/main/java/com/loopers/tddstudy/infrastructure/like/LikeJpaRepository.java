package com.loopers.tddstudy.infrastructure.like;

import com.loopers.tddstudy.domain.like.Like;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LikeJpaRepository extends JpaRepository<Like, Long> {

    Optional<Like> findByUserIdAndProductId(Long userId, Long productId);

    List<Like> findAllByUserId(Long userId);

    boolean existsByUserIdAndProductId(Long userId, Long productId);
}
