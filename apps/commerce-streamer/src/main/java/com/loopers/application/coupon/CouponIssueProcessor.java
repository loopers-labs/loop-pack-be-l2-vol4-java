package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponIssueResult;
import com.loopers.domain.coupon.CouponIssueResultRepository;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.eventhandled.EventHandled;
import com.loopers.domain.eventhandled.EventHandledRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@Slf4j
@RequiredArgsConstructor
@Component
public class CouponIssueProcessor {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final CouponIssueResultRepository resultRepository;
    private final EventHandledRepository eventHandledRepository;

    @Transactional
    public void handle(CouponIssueRequestMessage msg) {
        if (eventHandledRepository.existsByEventId(msg.requestId())) {
            return; // 멱등
        }
        CouponIssueResult result = resultRepository.findByRequestId(msg.requestId())
            .orElseGet(() -> resultRepository.save(
                CouponIssueResult.pending(msg.requestId(), msg.couponId(), msg.userId())));

        if (userCouponRepository.existsByUserIdAndCouponId(msg.userId(), msg.couponId())) {
            result.rejectDuplicate();
        } else if (couponRepository.tryConsumeQuantity(msg.couponId()) == 0) {
            result.rejectSoldOut();
        } else {
            Coupon coupon = couponRepository.find(msg.couponId())
                .orElseThrow(() -> new IllegalStateException("발급 대상 쿠폰이 없습니다: " + msg.couponId()));
            UserCoupon issued = userCouponRepository.save(new UserCoupon(
                msg.userId(), msg.couponId(), coupon.snapshot(), ZonedDateTime.now(), coupon.getExpiredAt()));
            result.issued(issued.getId());
        }
        resultRepository.save(result);
        record(msg.requestId());
    }

    private void record(String requestId) {
        try {
            eventHandledRepository.save(new EventHandled(requestId));
        } catch (DataIntegrityViolationException e) {
            // 경쟁 중복 — PK 충돌은 이미 처리로 간주
        }
    }
}
