package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 선착순 쿠폰 발급 요청 1건을 처리한다 (Consumer 가 호출).
 * - 멱등: 요청 상태가 PENDING 일 때만 처리 — 같은 요청이 재배달되어도 이미 확정된 결과는 바뀌지 않는다.
 * - 동시성: 쿠폰 행 비관적 락으로 수량 확인·차감을 직렬화. 같은 couponId 는 같은 파티션에서 순차 소비되지만,
 *   파티션 밖 경로(리밸런싱 중 중복 소비 등)까지 막는 DB 레벨 안전망이다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class CouponIssueProcessor {

    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    @Transactional
    public void process(String requestId) {
        CouponIssueRequest request = couponIssueRequestRepository.findByRequestId(requestId).orElse(null);
        if (request == null) {
            // 요청 행과 outbox 는 한 트랜잭션이므로 이 메시지가 왔다면 행도 있어야 한다 — 방어적 로그만 남긴다.
            log.warn("발급 요청을 찾을 수 없습니다. requestId={}", requestId);
            return;
        }
        if (!request.isPending()) {
            return; // 이미 확정(ISSUED/REJECTED)된 요청 — 중복 배달 skip (멱등)
        }

        Coupon coupon = couponRepository.findByIdForUpdate(request.getCouponId()).orElse(null);
        if (coupon == null) {
            request.reject("존재하지 않는 쿠폰입니다.");
            return;
        }
        if (userCouponRepository.existsByUserIdAndCouponId(request.getUserId(), request.getCouponId())) {
            request.reject("이미 발급받은 쿠폰입니다.");
            return;
        }
        if (!coupon.canIssue()) {
            request.reject("쿠폰 수량이 모두 소진되었습니다.");
            return;
        }

        coupon.issueOne();
        UserCoupon userCoupon = userCouponRepository.save(new UserCoupon(request.getUserId(), coupon.getId()));
        request.markIssued(userCoupon.getId());
    }
}
