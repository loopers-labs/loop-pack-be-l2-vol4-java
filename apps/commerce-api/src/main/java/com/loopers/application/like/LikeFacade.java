package com.loopers.application.like;

import com.loopers.domain.like.LikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class LikeFacade {

    private final LikeService likeService;

    public void like(Long userId, Long productId) {
        likeService.like(userId, productId);
    }

    public void unlike(Long userId, Long productId) {
        likeService.unlike(userId, productId);
    }

    public boolean isLiked(Long userId, Long productId) {
        return likeService.isLiked(userId, productId);
    }
}
