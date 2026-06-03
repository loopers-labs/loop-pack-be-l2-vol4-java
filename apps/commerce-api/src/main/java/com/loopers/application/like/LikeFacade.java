package com.loopers.application.like;

import com.loopers.application.product.ProductInfo;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class LikeFacade {

    private final LikeService likeService;
    private final ProductQueryService productQueryService;

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
     * 내가 좋아요한 상품 목록 (UC-07) — 협력 조회는 도메인 서비스(ProductQueryService)에 위임하고
     * Facade는 도메인 결과(ProductModel)를 응답 DTO로 변환만 한다.
     */
    public List<ProductInfo> getLikedProducts(Long userId, int page, int size) {
        return productQueryService.getMyLikedProducts(userId, page, size).stream()
                .map(ProductInfo::from)
                .toList();
    }
}
