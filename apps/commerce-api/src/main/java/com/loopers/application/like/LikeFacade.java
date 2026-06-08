package com.loopers.application.like;

import com.loopers.domain.like.ProductLikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class LikeFacade {
    private final ProductLikeService productLikeService;

    @Transactional
    public void like(Long userId, Long productId) {
        productLikeService.like(userId, productId);
    }

    @Transactional
    public void unlike(Long userId, Long productId) {
        productLikeService.unlike(userId, productId);
    }
}
