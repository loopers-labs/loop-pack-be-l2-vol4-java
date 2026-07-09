package com.loopers.domain.coupon;

import com.loopers.domain.metrics.EventHandledModel;
import com.loopers.domain.metrics.EventHandledRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 선착순 쿠폰 발급 처리. 한 트랜잭션에서 멱등 판단 + 중복/수량 검사 + 발급 + 요청 상태 갱신을 함께 커밋한다.
 * 같은 요청이 재전달돼도 event_handled 로 한 번만 반영된다(어떤 단계에서 롤백돼도 event_handled 도 함께 롤백 → 재시도 가능).
 */
@Service
@RequiredArgsConstructor
public class CouponIssueProcessor {

    private static final String EVENT_TYPE = "COUPON_ISSUE_REQUESTED";

    private final CouponIssueGateway couponIssueGateway;
    private final EventHandledRepository eventHandledRepository;

    @Transactional
    public void process(String eventId, String requestId, Long couponId, Long userId) {
        if (eventHandledRepository.exists(eventId)) {
            return;
        }
        eventHandledRepository.save(EventHandledModel.of(eventId, EVENT_TYPE));

        if (couponIssueGateway.alreadyIssued(userId, couponId)) {
            couponIssueGateway.markRejected(requestId, "이미 발급받은 쿠폰입니다.");
            return;
        }
        if (!couponIssueGateway.decreaseQuantity(couponId)) {
            couponIssueGateway.markRejected(requestId, "쿠폰이 모두 소진되었습니다.");
            return;
        }
        couponIssueGateway.insertUserCoupon(userId, couponId);
        couponIssueGateway.markIssued(requestId);
    }
}