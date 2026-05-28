package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class LikeRepositoryImpl implements LikeRepository {

    private final LikeJpaRepository likeJpaRepository;

    @Override
    public LikeModel save(LikeModel like) {
        return likeJpaRepository.save(like);
    }

    @Override
    public LikeModel saveAndFlush(LikeModel like) {
        return likeJpaRepository.saveAndFlush(like);
    }

    @Override
    public Optional<LikeModel> findByUserIdAndProductId(UUID userId, UUID productId) {
        return likeJpaRepository.findByUserIdAndProductId(userId, productId);
    }

    @Override
    public boolean existsByUserIdAndProductId(UUID userId, UUID productId) {
        return likeJpaRepository.existsByUserIdAndProductId(userId, productId);
    }

    @Override
    public Page<LikeModel> findAllByUserId(UUID userId, Pageable pageable) {
        return likeJpaRepository.findAllByUserId(userId, pageable);
    }

    @Override
    public Page<LikeModel> findAllByUserIdWithProduct(UUID userId, Pageable pageable) {
        return likeJpaRepository.findAllByUserIdWithProduct(userId, pageable);
    }

    @Override
    public void deleteByUserIdAndProductId(UUID userId, UUID productId) {
        likeJpaRepository.deleteByUserIdAndProductId(userId, productId);
    }
}
