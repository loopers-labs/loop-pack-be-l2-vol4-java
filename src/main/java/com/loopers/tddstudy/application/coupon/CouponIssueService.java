package com.loopers.tddstudy.application.coupon;

import com.loopers.tddstudy.domain.coupon.Coupon;
import com.loopers.tddstudy.domain.coupon.CouponRepository;
import com.loopers.tddstudy.domain.coupon.UserCoupon;
import com.loopers.tddstudy.domain.coupon.UserCouponRepository;
import com.loopers.tddstudy.infrastructure.coupon.CouponIssueResult;
import com.loopers.tddstudy.infrastructure.coupon.CouponIssueResultJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CouponIssueService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final CouponIssueResultJpaRepository resultRepository;

    public CouponIssueService(CouponRepository couponRepository,
                              UserCouponRepository userCouponRepository,
                              CouponIssueResultJpaRepository resultRepository) {
        this.couponRepository = couponRepository;
        this.userCouponRepository = userCouponRepository;
        this.resultRepository = resultRepository;
    }

    @Transactional
    public void issue(String requestId, Long couponId, Long userId) {
        // 멱등: 이미 확정(SUCCESS/FAILED)된 요청이면 재처리 안 함
        CouponIssueResult result = resultRepository.findById(requestId).orElse(null);
        if (result != null && !"PENDING".equals(result.getStatus())) {
            return;
        }
        try {
            Coupon coupon = couponRepository.findByIdWithLock(couponId)   // ← 비관적 락 = 동시성 제어 핵심
                    .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다."));
            if (coupon.isExpired()) {
                throw new IllegalArgumentException("만료된 쿠폰입니다.");
            }
            if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
                throw new IllegalArgumentException("이미 발급받은 쿠폰입니다.");
            }

            coupon.issue();                        // 한도 초과면 예외
            couponRepository.save(coupon);
            userCouponRepository.save(new UserCoupon(userId, couponId));

            resolve(requestId, couponId, userId, true, null);
        } catch (RuntimeException e) {
            // 비즈니스 예외 → 발급 실패로 기록 (락 조회 이후 DB 쓰기 전에 던져지므로 tx 오염 없음)
            resolve(requestId, couponId, userId, false, e.getMessage());
        }
    }

    private void resolve(String requestId, Long couponId, Long userId, boolean ok, String reason) {
        CouponIssueResult r = resultRepository.findById(requestId)
                .orElseGet(() -> new CouponIssueResult(requestId, couponId, userId));
        if (ok) r.success(); else r.fail(reason);
        resultRepository.save(r);
    }
}
