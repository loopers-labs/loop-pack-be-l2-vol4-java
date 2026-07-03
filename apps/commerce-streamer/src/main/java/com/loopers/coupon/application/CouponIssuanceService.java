package com.loopers.coupon.application;

import com.loopers.coupon.domain.Coupon;
import com.loopers.coupon.domain.CouponIssueDecision;
import com.loopers.coupon.domain.CouponIssueRequest;
import com.loopers.coupon.domain.CouponIssueRequestStatus;
import com.loopers.coupon.domain.CouponIssuancePolicy;
import com.loopers.coupon.domain.UserCoupon;
import com.loopers.coupon.infrastructure.CouponIssueRequestJpaRepository;
import com.loopers.coupon.infrastructure.CouponJpaRepository;
import com.loopers.coupon.infrastructure.UserCouponJpaRepository;
import com.loopers.coupon.interfaces.CouponIssueRequestedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 선착순 쿠폰 발급. 멱등을 별도 dedup 테이블 없이 "설계로" 확보한다.
 * - 멱등: coupon_issue_request.status 가 곧 처리 가드(PENDING 이 아니면 이미 처리됨 → skip).
 * - 수량: 원자적 조건부 차감(tryConsumeQuota)이 초과 발급을 막음(락·파티션 직렬화 불필요).
 * - 중복 발급: (coupon_id, user_id) 유니크가 최종 방어선.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponIssuanceService {

    private final CouponJpaRepository couponJpaRepository;
    private final UserCouponJpaRepository userCouponJpaRepository;
    private final CouponIssueRequestJpaRepository couponIssueRequestJpaRepository;
    private final CouponIssuancePolicy couponIssuancePolicy;

    @Transactional
    public void issue(CouponIssueRequestedMessage message) {
        CouponIssueRequest request = couponIssueRequestJpaRepository.findByRequestId(message.requestId()).orElse(null);
        if (request == null) {
            log.warn("발급 요청 레코드 없음 — skip requestId={}", message.requestId());
            return;
        }
        if (request.getStatus() != CouponIssueRequestStatus.PENDING) {
            return; // 이미 처리된 요청(재전달) — 요청 행 상태가 멱등 가드
        }

        Coupon coupon = couponJpaRepository.findById(message.couponId()).orElse(null);
        boolean alreadyIssued = userCouponJpaRepository.existsByCouponIdAndUserId(message.couponId(), message.userId());

        switch (couponIssuancePolicy.decide(coupon, alreadyIssued)) {
            case NOT_FOUND -> reject(request, "쿠폰이 존재하지 않습니다");
            case DUPLICATE -> reject(request, "이미 발급된 사용자입니다");
            case PROCEED -> {
                if (couponJpaRepository.tryConsumeQuota(coupon.getId()) == 0) {
                    reject(request, "발급 수량이 소진되었습니다");
                } else {
                    userCouponJpaRepository.save(UserCoupon.issue(coupon, message.userId()));
                    request.markSuccess();
                }
            }
        }
    }

    private void reject(CouponIssueRequest request, String reason) {
        log.info("쿠폰 발급 거절 reason={} requestId={}", reason, request.getRequestId());
        request.markRejected(reason);
    }
}
