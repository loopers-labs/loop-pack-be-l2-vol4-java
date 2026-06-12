package com.loopers.domain.like;

import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 좋아요 등록/취소는 **멱등** 하다.
 * - 등록: 이미 누른 상태면 카운트 증가 없이 통과
 * - 취소: 누른 적 없으면 카운트 감소 없이 통과
 */
@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeRepository likeRepository;
    private final ProductService productService;

    @Transactional
    public void like(Long userId, Long productId) {
        // 사전 존재 검증 — 존재하지 않는 상품이면 NOT_FOUND
        productService.getProduct(productId);

        if (likeRepository.existsByUserIdAndProductId(userId, productId)) {
            return; // 멱등 통과
        }
        try {
            likeRepository.save(new LikeModel(userId, productId));
            productService.incrementLikeCount(productId);
        } catch (DataIntegrityViolationException race) {
            // 동시 요청으로 UNIQUE 제약 위반 — 멱등 통과
        }
    }

    @Transactional
    public void unlike(Long userId, Long productId) {
        int affected = likeRepository.deleteByUserIdAndProductId(userId, productId);
        if (affected > 0) {
            productService.decrementLikeCount(productId);
        }
    }

    @Transactional(readOnly = true)
    public List<LikeModel> findAllByUser(Long userId) {
        return likeRepository.findAllByUserId(userId);
    }

    @Transactional(readOnly = true)
    public boolean isLiked(Long userId, Long productId) {
        return likeRepository.existsByUserIdAndProductId(userId, productId);
    }
}
