package com.loopers.domain.like;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeRepository likeRepository;

    @Transactional
    public Like like(Long userId, Long productId) {
        return likeRepository.findByUserIdAndProductId(userId, productId)
            .orElseGet(() -> likeRepository.save(Like.create(userId, productId)));
    }

    @Transactional(readOnly = true)
    public long countProductLikes(Long productId) {
        return likeRepository.countByProductId(productId);
    }
}
