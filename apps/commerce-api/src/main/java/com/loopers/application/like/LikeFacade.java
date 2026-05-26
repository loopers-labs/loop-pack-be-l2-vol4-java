package com.loopers.application.like;

import com.loopers.application.product.ProductInfo;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class LikeFacade {
    private final LikeService likeService;
    private final ProductService productService;

    public void like(Long userId, Long productId) {
        productService.getProduct(productId); // 상품 존재 확인 (없으면 NOT_FOUND)
        likeService.like(userId, productId);
    }

    public void unlike(Long userId, Long productId) {
        productService.getProduct(productId); // 상품 존재 확인 (없으면 NOT_FOUND)
        likeService.unlike(userId, productId);
    }

    /**
     * 내가 좋아요 한 상품 목록을 조회한다.
     */
    public List<ProductInfo> getLikedProducts(Long userId) {
        List<Long> productIds = likeService.getLikedProductIds(userId);
        return productService.getProductsByIds(productIds).stream()
            .map(ProductInfo::from)
            .toList();
    }
}
