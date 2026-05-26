package com.loopers.application.like;

import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.Product;
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
        Product product = productService.getProduct(productId);
        if (likeService.like(userId, productId)) {
            product.increaseLikeCount();
        }
    }

    @Transactional
    public void unlike(Long userId, Long productId) {
        Product product = productService.getProduct(productId);
        if (likeService.unlike(userId, productId)) {
            product.decreaseLikeCount();
        }
    }
}
