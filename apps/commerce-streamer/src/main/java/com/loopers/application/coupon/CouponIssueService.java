package com.loopers.application.coupon;

import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.infrastructure.coupon.CouponIssueRequestJpaRepository;
import com.loopers.infrastructure.coupon.CouponStockJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 발급 요청 1건을 처리한다. 조건부 UPDATE로 수량을 확보한 경우에만 실제 발급(user_coupon insert).
 * 처리 결과에 따라 coupon_issue_request 상태를 ISSUED / SOLD_OUT으로 갱신한다(폴링 조회용).
 * <p>
 * 처리 단위 = 요청 1건 = 트랜잭션 1개. 상태 갱신도 같은 트랜잭션이라, 롤백 시 상태는 PENDING으로 남아
 * 재전달 시 다시 처리된다(수렴).
 */
@RequiredArgsConstructor
@Service
public class CouponIssueService {

    private static final String ISSUED = "ISSUED";
    private static final String SOLD_OUT = "SOLD_OUT";

    private final CouponStockJpaRepository couponStockJpaRepository;
    private final UserCouponJpaRepository userCouponJpaRepository;
    private final CouponIssueRequestJpaRepository couponIssueRequestJpaRepository;

    @Transactional
    public void issue(String requestId, Long couponId, Long userId) {
        // 1) 멱등 1차: 이미 발급받았으면 재고를 건드리지 않고 스킵(상태는 ISSUED로 확정).
        //    (반드시 tryIssue 앞. 뒤에 두면 재고만 깎이고 발급은 스킵되는 누수 발생)
        if (userCouponJpaRepository.existsByUserIdAndCouponId(userId, couponId)) {
            couponIssueRequestJpaRepository.updateStatus(requestId, ISSUED);
            return;
        }

        // 2) 수량 확보: 판단은 DB의 WHERE 절이 원자적으로 끝냈다. 여기선 결과(행 수)를 확인만 한다.
        int updated = couponStockJpaRepository.tryIssue(couponId);
        if (updated == 0) {
            // 이미 소진 — 발급하지 않고 상태만 SOLD_OUT으로.
            couponIssueRequestJpaRepository.updateStatus(requestId, SOLD_OUT);
            return;
        }

        // 3) 발급. 순서 가정이 깨져 중복이 뚫리면 (user_id, coupon_id) UNIQUE 제약이 최후로 막고,
        //    위반 예외로 이 트랜잭션이 롤백되어 2)의 재고 차감·상태 갱신까지 되돌아간다(단일 트랜잭션).
        //    이후 재전달되면 1)의 exists 체크가 걸러낸다.
        userCouponJpaRepository.save(new UserCouponModel(userId, couponId));
        couponIssueRequestJpaRepository.updateStatus(requestId, ISSUED);
    }
}
