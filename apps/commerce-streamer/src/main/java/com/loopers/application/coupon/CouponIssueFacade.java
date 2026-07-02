package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponQuotaRepository;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.idempotency.EventHandledRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CouponIssueFacade {

    private final EventHandledRepository eventHandledRepository;
    private final CouponQuotaRepository couponQuotaRepository;
    private final UserCouponRepository userCouponRepository;
    private final CouponIssueRequestRepository couponIssueRequestRepository;

    /**
     * 한 배치를 한 트랜잭션으로 처리한다. 처음 보는 이벤트면(markIfFirst) 표시와 실제 발급을 같은 TX 로 묶어
     * 원자성·멱등을 함께 보장한다 (batch + manual ack 의 재전송을 멱등이 흡수).
     */
    @Transactional
    public void handle(List<CouponIssueMessage> messages) {
        for (CouponIssueMessage message : messages) {
            if (eventHandledRepository.markIfFirst(message.eventId())) {
                issue(message);
            }
        }
    }

    private void issue(CouponIssueMessage message) {
        CouponIssueRequest issueRequest = couponIssueRequestRepository.findById(message.data().requestId()).orElseThrow();

        // 이미 보유한 유저면 한도를 소비하기 "전에" 거절한다 — 중복이 선착순 슬롯을 잡아먹지 않도록 순서가 핵심.
        // key=couponPolicyId 로 같은 정책 이벤트가 단일 파티션에서 순차 소비되므로 이 check-then-act 는 안전하다.
        if (userCouponRepository.existsByUserIdAndCouponPolicyId(message.data().userId(), message.data().couponPolicyId())) {
            issueRequest.markRejected();
            return;
        }

        if (couponQuotaRepository.increaseIssued(message.data().couponPolicyId()) == 0) {
            issueRequest.markRejected();
            return;
        }

        UserCoupon issued = UserCoupon.issue(message.data().userId(), message.data().couponPolicyId(), message.data().type(),
            message.data().discountValue(), message.data().minOrderAmount(), message.data().expiredAt());
        userCouponRepository.save(issued);
        issueRequest.markIssued();
    }
}
