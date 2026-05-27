package com.loopers.domain.like;

import com.loopers.domain.product.ProductService;
import com.loopers.support.page.PagePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;

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

    /** 본인 활성 좋아요 목록 — 좋아요 시점 최신순 페이지 조회 (UC-07). 상품 조합은 Facade가 한다. */
    @Transactional(readOnly = true)
    public List<LikeModel> getMyActiveLikes(Long userId, int page, int size) {
        PagePolicy.validate(page, size);
        return likeRepository.findActiveByUserId(userId, page, size);
    }

    /** 주어진 상품들 중 사용자가 좋아요한 productId 집합 — 목록 좋아요 여부 batch 조회 (UC-03). */
    @Transactional(readOnly = true)
    public Set<Long> findLikedProductIds(Long userId, Collection<Long> productIds) {
        return likeRepository.findLikedProductIds(userId, productIds);
    }

    /**
     * 특정 상품의 활성 좋아요를 전부 soft delete (Product 비활성 시 cascade 전파 — 01 §7.5).
     * 멱등 — 활성 좋아요가 없으면 no-op. 상품 자체가 비활성이 되므로 likesCount는 건드리지 않는다.
     */
    @Transactional
    public void deactivateByProduct(Long productId) {
        for (LikeModel like : likeRepository.findActiveByProductId(productId)) {
            like.delete();
            likeRepository.save(like);
        }
    }
}
