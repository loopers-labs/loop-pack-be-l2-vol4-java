package com.loopers.infrastructure.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class LikeRepositoryImpl implements LikeRepository {
    private final LikeJpaRepository likeJpaRepository;

    @Override
    public boolean existsBy(Long userId, Long productId) {
        return likeJpaRepository.existsByUserIdAndProductId(userId, productId);
    }

    @Override
    public void save(Like like) {
        likeJpaRepository.save(like);
    }

    @Override
    public void deleteBy(Long userId, Long productId) {
        likeJpaRepository.findByUserIdAndProductId(userId, productId)
            .ifPresent(likeJpaRepository::delete);
    }

    @Override
    public List<Like> findByUserId(Long userId) {
        return likeJpaRepository.findByUserId(userId);
    }
}
