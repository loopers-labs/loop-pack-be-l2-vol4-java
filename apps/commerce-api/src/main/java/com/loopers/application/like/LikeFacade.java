package com.loopers.application.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeCountRepository;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.like.ProductLikeCount;
import com.loopers.domain.like.event.LikeAdded;
import com.loopers.domain.like.event.LikeRemoved;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Component
public class LikeFacade {

    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final LikeCountRepository likeCountRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void like(String loginId, Long productId) {
        Long userId = resolveUserId(loginId);
        if (likeRepository.existsBy(userId, productId)) {
            return; // 멱등: 이미 좋아요한 경우
        }
        productRepository.find(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다."));

        likeRepository.save(new Like(userId, productId));
        // 운영 카운트: API 가 즉시 정확해야 하므로 트랜잭션 안에서 원자적 upsert (동시성 정합성 보장)
        likeCountRepository.increase(productId);
        // 이벤트: 로깅/분석집계(Step2 product_metrics)로 분리 — 후속 처리는 좋아요 성공과 무관
        ProductLikeCount snapshot = likeCountRepository.find(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "좋아요 집계를 찾을 수 없습니다."));
        eventPublisher.publishEvent(new LikeAdded(
            userId, productId, snapshot.getCount(), snapshot.getVersion(), ZonedDateTime.now()));
    }

    @Transactional
    public void unlike(String loginId, Long productId) {
        Long userId = resolveUserId(loginId);
        if (!likeRepository.existsBy(userId, productId)) {
            return; // 멱등: 좋아요하지 않은 경우
        }
        likeRepository.deleteBy(userId, productId);
        likeCountRepository.decrease(productId);
        ProductLikeCount snapshot = likeCountRepository.find(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "좋아요 집계를 찾을 수 없습니다."));
        eventPublisher.publishEvent(new LikeRemoved(
            userId, productId, snapshot.getCount(), snapshot.getVersion(), ZonedDateTime.now()));
    }

    private Long resolveUserId(String loginId) {
        return userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다."))
            .getId();
    }
}
