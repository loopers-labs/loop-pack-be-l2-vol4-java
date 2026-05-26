package com.loopers.domain.like;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeRepository likeRepository;

    @Transactional
    public boolean like(Long userId, Long productId) {
        if (likeRepository.existsByUserIdAndProductId(userId, productId)) {
            return false;
        }
        likeRepository.save(new LikeModel(userId, productId));
        return true;
    }

    @Transactional
    public boolean unlike(Long userId, Long productId) {
        return likeRepository.deleteByUserIdAndProductId(userId, productId) > 0;
    }
}
