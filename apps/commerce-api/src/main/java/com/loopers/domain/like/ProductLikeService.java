package com.loopers.domain.like;

import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductLikeService {
    private final LikeService likeService;
    private final ProductService productService;

    public void like(Long userId, Long productId) {
        productService.getProduct(productId);
        if (likeService.like(userId, productId)) {
            productService.increaseLikeCount(productId);
        }
    }

    public void unlike(Long userId, Long productId) {
        productService.getProduct(productId);
        if (likeService.unlike(userId, productId)) {
            productService.decreaseLikeCount(productId);
        }
    }
}
