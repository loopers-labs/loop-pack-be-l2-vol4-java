package com.loopers.application.like;

import com.loopers.application.product.ProductCacheService;
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
    private final ProductCacheService productCacheService;

    @Transactional
    public void like(Long memberId, Long productId) {
        boolean liked = likeService.like(memberId, productId);
        if (liked) {
            productService.incrementLikeCount(productId);
            productCacheService.evictProductDetail(productId);
            productCacheService.evictAllProductLists();
        }
    }

    @Transactional
    public void unlike(Long memberId, Long productId) {
        boolean unliked = likeService.unlike(memberId, productId);
        if (unliked) {
            productService.decrementLikeCount(productId);
            productCacheService.evictProductDetail(productId);
            productCacheService.evictAllProductLists();
        }
    }
}
