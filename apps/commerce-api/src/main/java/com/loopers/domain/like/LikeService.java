package com.loopers.domain.like;

import com.loopers.domain.event.LikeChangedEvent;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 좋아요 등록/취소는 <b>멱등</b> 하다.
 * - 등록: 이미 누른 상태면 카운트 증가/이벤트 발행 없이 통과
 * - 취소: 누른 적 없으면 카운트 감소/이벤트 발행 없이 통과
 * <p>
 * 상태가 실제로 변할 때에만 {@link LikeChangedEvent} 를 발행한다 — 리스너가 outbox 저장 / 로깅 / 캐시 무효화를 담당한다.
 */
@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeRepository likeRepository;
    private final ProductService productService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void like(Long userId, Long productId) {
        productService.getProduct(productId); // 존재 검증

        if (likeRepository.existsByUserIdAndProductId(userId, productId)) {
            return;
        }
        try {
            likeRepository.save(new LikeModel(userId, productId));
            productService.incrementLikeCount(productId);
            eventPublisher.publishEvent(LikeChangedEvent.liked(userId, productId));
        } catch (DataIntegrityViolationException race) {
            // 동시 요청으로 UNIQUE 제약 위반 — 멱등 통과 (이벤트도 발행 안 함)
        }
    }

    @Transactional
    public void unlike(Long userId, Long productId) {
        int affected = likeRepository.deleteByUserIdAndProductId(userId, productId);
        if (affected > 0) {
            productService.decrementLikeCount(productId);
            eventPublisher.publishEvent(LikeChangedEvent.unliked(userId, productId));
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
