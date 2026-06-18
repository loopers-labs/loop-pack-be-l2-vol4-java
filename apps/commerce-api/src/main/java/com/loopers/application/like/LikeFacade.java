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
    public void like(Long memberId, Long productId) {
        boolean liked = likeService.like(memberId, productId);
        if (liked) {
            productService.incrementLikeCount(productId);
        }
    }

    @Transactional
    public void unlike(Long memberId, Long productId) {
        boolean unliked = likeService.unlike(memberId, productId);
        if (unliked) {
            productService.decrementLikeCount(productId);
        }
    }
}
