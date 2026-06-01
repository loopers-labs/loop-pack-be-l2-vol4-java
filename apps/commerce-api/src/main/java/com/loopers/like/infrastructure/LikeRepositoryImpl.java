package com.loopers.like.infrastructure;

import com.loopers.like.domain.LikeModel;
import com.loopers.like.domain.LikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class LikeRepositoryImpl implements LikeRepository {

    private final LikeJpaRepository likeJpaRepository;

    @Override
    public LikeModel save(LikeModel like) {
        return likeJpaRepository.save(like);
    }

    @Override
    public Optional<LikeModel> findByUserIdAndProductId(Long userId, Long productId) {
        return likeJpaRepository.findByUserIdAndProductId(userId, productId);
    }

    @Override
    public List<LikeModel> findAllByUserId(Long userId) {
        return likeJpaRepository.findAllByUserId(userId);
    }

    @Override
    public void delete(LikeModel like) {
        likeJpaRepository.delete(like);
    }
}
