package com.loopers.application.like;

import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class LikeFacade {

    private final LikeService likeService;
    private final ProductService productService;

    @Transactional
    public void like(Long userId, Long productId) {
        ProductModel product = productService.getActive(productId);

        if (likeService.like(userId, productId)) {
            product.incrementLikeCount();
        }
    }

    @Transactional
    public void unlike(Long userId, Long productId) {
        ProductModel product = productService.getActive(productId);

        if (likeService.unlike(userId, productId)) {
            product.decrementLikeCount();
        }
    }
}
