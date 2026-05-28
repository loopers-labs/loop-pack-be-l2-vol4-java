package com.loopers.domain.like;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductLikeService {
    private final LikeService likeService;
    private final ProductService productService;

    public void like(Long userId, Long productId) {
        Product product = productService.getProduct(productId);
        if (likeService.like(userId, productId)) {
            product.increaseLikeCount();
        }
    }

    public void unlike(Long userId, Long productId) {
        Product product = productService.getProduct(productId);
        if (likeService.unlike(userId, productId)) {
            product.decreaseLikeCount();
        }
    }
}
