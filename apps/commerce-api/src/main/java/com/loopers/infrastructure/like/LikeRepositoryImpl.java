package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class LikeRepositoryImpl implements LikeRepository {

    private final LikeJpaRepository likeJpaRepository;

    @Override
    public boolean existsByUserIdAndProductId(Long userId, Long productId) {
        return likeJpaRepository.existsByUserIdAndProductId(userId, productId);
    }

    @Override
    public List<LikeModel> findByUserId(Long userId) {
        return likeJpaRepository.findByUserId(userId);
    }

    @Override
    public LikeModel save(LikeModel like) {
        return likeJpaRepository.save(like);
    }

    @Override
    public LikeModel saveAndFlush(LikeModel like) {
        return likeJpaRepository.saveAndFlush(like);
    }

    @Override
    public int delete(Long userId, Long productId) {
        return likeJpaRepository.deleteByUserIdAndProductId(userId, productId);
    }

    @Override
    public Optional<LikeModel> findByUserIdAndProductId(Long userId, Long productId) {
        return likeJpaRepository.findByUserIdAndProductId(userId, productId);
    }
}
