package com.loopers.domain.like;

import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductLikeService {
    private final LikeService likeService;
    private final ProductService productService;

    /** @return 좋아요가 새로 생겼으면 true (이미 눌려 있었으면 false) */
    public boolean like(Long userId, Long productId) {
        productService.getProduct(productId);
        return likeService.like(userId, productId);
    }

    /** @return 좋아요가 실제로 제거됐으면 true (원래 없었으면 false) */
    public boolean unlike(Long userId, Long productId) {
        productService.getProduct(productId);
        return likeService.unlike(userId, productId);
    }
}
