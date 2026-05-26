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
        likeService.like(userId, productId);
    }

    public void unlike(Long userId, Long productId) {
        likeService.unlike(userId, productId);
    }

    public boolean isLiked(Long userId, Long productId) {
        return likeService.isLiked(userId, productId);
    }

    /**
     * 내가 좋아요한 상품 목록 (UC-07) — 좋아요 시점 최신순. 좋아요는 살아있어도 상품·브랜드가
     * 비활성된 경우 결과에서 제외한다(productService.findActive가 활성만 반환).
     */
    public List<ProductInfo> getLikedProducts(Long userId, int page, int size) {
        return likeService.getMyActiveLikes(userId, page, size).stream()
            .map(like -> productService.findActive(like.getProductId()).orElse(null))
            .filter(java.util.Objects::nonNull)
            .map(ProductInfo::from)
            .toList();
    }
}
