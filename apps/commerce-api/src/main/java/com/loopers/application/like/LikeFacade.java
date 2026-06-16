package com.loopers.application.like;

import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LikeFacade {

    private final LikeService likeService;
    private final ProductService productService;

    public void addLike(Long userId, Long productId) {
        // 1. 상품 존재 여부 조회 (일반 SELECT)
        productService.getProduct(productId);

        // 2. 이미 좋아요를 눌렀는지 확인 (멱등성 보장)
        if (likeService.existsLikeRecord(userId, productId)) {
            return;
        }

        // 3. 좋아요 추가
        likeService.addLikeRecord(userId, productId);
    }

    public void removeLike(Long userId, Long productId) {
        // 1. 상품 존재 여부 조회 (일반 SELECT)
        productService.getProduct(productId);

        // 2. 좋아요가 존재하는 경우에만 삭제 (멱등성 보장)
        if (!likeService.existsLikeRecord(userId, productId)) {
            return;
        }

        // 3. 좋아요 삭제
        likeService.removeLikeRecord(userId, productId);
    }
}
