package com.loopers.domain.like;

import com.loopers.domain.product.ProductModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Like + Product 크로스 애그리거트 도메인 서비스.
 *
 * 좋아요/취소 시 Product.likeCount 변경이 항상 함께 발생하는 비즈니스 규칙을 담는다.
 * - LikeModel에 넣으면 ProductModel을 알아야 함 (불가)
 * - ProductModel에 넣으면 LikeService를 알아야 함 (불가)
 * → 어느 쪽에도 속하지 않는 크로스 애그리거트 규칙 → Domain Service
 */
@RequiredArgsConstructor
@Component
public class ProductLikeService {

    private final LikeService likeService;

    /** 좋아요 — 신규이면 Like 저장 + Product.likeCount 증가 (멱등) */
    public void like(UUID userId, UUID productId, ProductModel product) {
        boolean isNew = likeService.find(userId, productId).isEmpty();
        if (isNew) {
            likeService.like(userId, productId);
            product.incrementLikeCount();
        }
    }

    /** 좋아요 취소 — 삭제되었으면 Product.likeCount 감소 (멱등) */
    public void unlike(UUID userId, UUID productId, ProductModel product) {
        boolean deleted = likeService.unlike(userId, productId);
        if (deleted) {
            product.decrementLikeCount();
        }
    }
}
