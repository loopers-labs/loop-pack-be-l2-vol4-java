package com.loopers.application.like;

import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class LikeFacade {

    private final LikeService likeService;
    private final ProductService productService;

    @Transactional
    public void like(Long userId, Long productId) {
        productService.getById(productId);
        if (likeService.like(userId, productId)) {
            productService.incrementLikeCount(productId);
        }
    }

    @Transactional
    public void unlike(Long userId, Long productId) {
        if (likeService.unlike(userId, productId)) {
            productService.decrementLikeCount(productId);
        }
    }
}
