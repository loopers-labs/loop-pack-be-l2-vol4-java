package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LikeRepositoryImpl implements LikeRepository {

    private final LikeJpaRepository likeJpaRepository;

    @Override
    public boolean saveIfAbsent(LikeModel like) {
        if (likeJpaRepository.existsByUserIdAndProductId(like.getUserId(), like.getProductId())) {
            return false;
        }
        likeJpaRepository.save(like);
        return true;
    }

    @Override
    public int deleteByUserIdAndProductId(Long userId, Long productId) {
        return likeJpaRepository.deleteByUserIdAndProductId(userId, productId);
    }
}
