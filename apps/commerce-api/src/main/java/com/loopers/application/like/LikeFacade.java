package com.loopers.application.like;

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

    /**
     * 존재 검증을 하위 레이어로 위임:
     *  - like: incrementLikeCount의 atomic UPDATE 0건 → NOT_FOUND (상품 부재 의미)
     *  - unlike: likeService.unlike가 1건 이상 지웠다면 상품은 직전에 존재. 0건이면 requireExists로 NOT_FOUND/멱등 구분.
     * Facade는 Service ↔ Service 합성 + 트랜잭션 경계만 가지며, 사전 조회를 중복 수행하지 않는다.
     */
    @Transactional
    public void like(Long userId, Long productId) {
        if (likeService.like(userId, productId)) {
            productService.incrementLikeCount(productId);
        } else {
            productService.requireExists(productId);
        }
    }

    @Transactional
    public void unlike(Long userId, Long productId) {
        if (likeService.unlike(userId, productId)) {
            productService.decrementLikeCount(productId);
        } else {
            productService.requireExists(productId);
        }
    }
}
