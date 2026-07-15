package com.loopers.application.like;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductLikeFacade {

    private final ProductLikeService productLikeService;

    public void like(String userId, Long productId) {
        productLikeService.like(userId, productId);
    }

    public void unlike(String userId, Long productId) {
        productLikeService.unlike(userId, productId);
    }
}
