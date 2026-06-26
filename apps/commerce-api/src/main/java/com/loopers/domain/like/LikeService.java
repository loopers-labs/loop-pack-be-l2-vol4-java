package com.loopers.domain.like;

import com.loopers.domain.like.event.ProductLikedEvent;
import com.loopers.domain.like.event.ProductUnlikedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 좋아요 도메인 서비스. 등록/취소는 멱등하게 동작한다.
 *
 * 좋아요 수는 product_like 의 row 집계가 아니라 ProductLikeStat (read-model) 에 비정규화된
 * like_count 컬럼으로 관리한다. 등록/취소 시 이벤트를 발행해 핸들러가 commit 직후 갱신한다.
 *  → 사용자 응답 경로(이 트랜잭션)는 짧게 유지하고, stat 갱신의 락 경합은 격리.
 */
@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeRepository likeRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void like(Long userId, Long productId) {
        if (likeRepository.existsBy(userId, productId)) {
            return; // 이미 좋아요한 상태이면 멱등하게 아무 동작도 하지 않는다.
        }
        likeRepository.save(Like.of(userId, productId));
        eventPublisher.publishEvent(new ProductLikedEvent(userId, productId));
    }

    @Transactional
    public void unlike(Long userId, Long productId) {
        if (!likeRepository.existsBy(userId, productId)) {
            return; // 좋아요하지 않은 상태이면 멱등하게 아무 동작도 하지 않는다.
        }
        likeRepository.deleteBy(userId, productId);
        eventPublisher.publishEvent(new ProductUnlikedEvent(userId, productId));
    }

    @Transactional(readOnly = true)
    public long getLikeCount(Long productId) {
        return likeRepository.countByProductId(productId);
    }

    @Transactional(readOnly = true)
    public List<Long> getLikedProductIds(Long userId) {
        return likeRepository.findProductIdsByUserId(userId);
    }
}
