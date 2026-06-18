package com.loopers.application.like;

import com.loopers.domain.like.LikeCountStore;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class LikeFacade {

    private final LikeService likeService;
    private final ProductService productService;
    private final LikeCountStore likeCountStore;

    /**
     * 좋아요 등록. 관계(product_like)는 RDB가 원천이자 중복 차단 게이트이고,
     * 좋아요 수 증감은 Redis로 흡수한다(배치가 product.like_count에 주기 반영).
     * 트랜잭션은 각 도메인 서비스가 자체 관리한다 — 관계가 커밋된 뒤에만 증감분을 올려 과다 집계를 막는다.
     */
    public void like(Long userId, Long productId) {
        productService.requireExists(productId);
        if (likeService.like(userId, productId)) {
            likeCountStore.increment(productId);
        }
    }

    public void unlike(Long userId, Long productId) {
        if (likeService.unlike(userId, productId)) {
            likeCountStore.decrement(productId);
        } else {
            productService.requireExists(productId);
        }
    }
}
