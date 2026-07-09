package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 선착순 발급 요청 접수/조회 (Slice 4, Step3). 실제 발급(수량 확보)은 하지 않고 요청만 남긴다 — API는 발행만 하고 즉시 202.
 *
 * <p><b>멱등(1인 1매)</b>: 같은 (userId, couponId)로 재요청하면 새 요청을 만들지 않고 기존 요청을 그대로 반환한다.
 * 요청 행 INSERT는 {@code UNIQUE(user_id, coupon_id)}가 최종 방어선이며, 신규 접수일 때만
 * {@link CouponIssueRequestedEvent}를 발행한다(→ 커밋 직전 리스너가 outbox 적재).
 */
@Component
@RequiredArgsConstructor
public class CouponIssueRequestService {

    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public CouponIssueRequestModel request(Long userId, Long couponId) {
        Optional<CouponIssueRequestModel> existing = couponIssueRequestRepository.findByUserIdAndCouponId(userId, couponId);
        if (existing.isPresent()) {
            return existing.get(); // 멱등 — 이미 접수된 요청 재반환(이벤트 재발행 없음)
        }
        CouponIssueRequestModel saved = couponIssueRequestRepository.save(new CouponIssueRequestModel(userId, couponId));
        eventPublisher.publishEvent(CouponIssueRequestedEvent.from(saved));
        return saved;
    }

    @Transactional(readOnly = true)
    public CouponIssueRequestModel getResult(Long requestId) {
        return couponIssueRequestRepository.find(requestId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        "[requestId = " + requestId + "] 발급 요청을 찾을 수 없습니다."));
    }
}
