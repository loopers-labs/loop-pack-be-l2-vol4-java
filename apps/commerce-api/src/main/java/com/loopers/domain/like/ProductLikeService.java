package com.loopers.domain.like;

import com.loopers.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Like + Product 크로스 애그리거트 도메인 서비스.
 *
 * 좋아요/취소 시 Product.likeCount 변경이 항상 함께 발생하는 비즈니스 규칙을 담는다.
 * 동시 좋아요 시 Lost Update 방지를 위해 likeCount 는 원자적 UPDATE(ProductRepository)로 증감한다.
 */
@RequiredArgsConstructor
@Component
public class ProductLikeService {

    private final LikeService likeService;
    private final ProductRepository productRepository;

    /** 좋아요 — 멱등 삽입이 실제로 새로 넣었을 때만 likeCount 원자적 증가 */
    public void like(UUID userId, UUID productId) {
        if (likeService.like(userId, productId)) {
            productRepository.incrementLikeCount(productId);
        }
    }

    /** 좋아요 취소 — 실제로 삭제됐을 때만 likeCount 원자적 감소 */
    public void unlike(UUID userId, UUID productId) {
        if (likeService.unlike(userId, productId)) {
            productRepository.decrementLikeCount(productId);
        }
    }
}
