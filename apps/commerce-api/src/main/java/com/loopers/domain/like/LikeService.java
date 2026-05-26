package com.loopers.domain.like;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LikeService {

    private final LikeRepository likeRepository;

    public boolean like(Long userId, Long productId) {
        return likeRepository.saveIfAbsent(new LikeModel(userId, productId));
    }

    public boolean unlike(Long userId, Long productId) {
        return likeRepository.deleteByUserIdAndProductId(userId, productId) > 0;
    }
}
