package com.loopers.domain.like;

import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductLikeService {
    private final LikeService likeService;
    private final ProductService productService;
    private final ApplicationEventPublisher eventPublisher;

    public void like(Long userId, Long productId) {
        productService.getProduct(productId);
        if (likeService.like(userId, productId)) {
            eventPublisher.publishEvent(new ProductLikedEvent(userId, productId));
        }
    }

    public void unlike(Long userId, Long productId) {
        productService.getProduct(productId);
        if (likeService.unlike(userId, productId)) {
            eventPublisher.publishEvent(new ProductUnlikedEvent(userId, productId));
        }
    }
}
