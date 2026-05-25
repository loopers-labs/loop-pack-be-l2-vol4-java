package com.loopers.domain.like;

import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeRepository likeRepository;
    private final ProductService productService;

    /**
     * 좋아요 등록 (멱등). 활성 상품 검증 → 신규 INSERT 또는 reactivate → likesCount +1.
     * 이미 좋아요 상태면 no-op (카운터 변화 없음). 좋아요 변화와 카운터 증가는 동일 트랜잭션 (04 §4.2).
     */
    @Transactional
    public void like(Long userId, Long productId) {
        productService.getActiveProduct(productId); // 활성 상품만 좋아요 가능 (없으면 NOT_FOUND)

        LikeModel like = likeRepository.findByUserIdAndProductId(userId, productId).orElse(null);
        if (like != null && like.isActive()) {
            return; // 이미 좋아요 — 멱등 no-op
        }
        if (like == null) {
            like = new LikeModel(userId, productId);
        } else {
            like.reactivate();
        }
        likeRepository.save(like);
        productService.increaseLikesCount(productId);
    }

    /**
     * 좋아요 취소 (멱등). 활성 좋아요면 soft delete → likesCount -1.
     * 좋아요가 없거나 이미 취소 상태면 no-op (카운터 변화 없음).
     */
    @Transactional
    public void unlike(Long userId, Long productId) {
        LikeModel like = likeRepository.findByUserIdAndProductId(userId, productId).orElse(null);
        if (like == null || !like.isActive()) {
            return; // 좋아요 없음 — 멱등 no-op
        }
        like.delete();
        likeRepository.save(like);
        productService.decreaseLikesCount(productId);
    }

    @Transactional(readOnly = true)
    public boolean isLiked(Long userId, Long productId) {
        return likeRepository.findByUserIdAndProductId(userId, productId)
                .map(LikeModel::isActive)
                .orElse(false);
    }
}
