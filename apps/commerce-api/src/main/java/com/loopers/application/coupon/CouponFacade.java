package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class CouponFacade {

    private final UserService userService;
    private final CouponService couponService;
    private final ApplicationEventPublisher eventPublisher;

    public UserCouponInfo issue(String loginId, String loginPw, Long couponId) {
        UserModel user = userService.getUser(loginId, loginPw);
        UserCouponModel userCoupon = couponService.issueCoupon(user.getId(), couponId);
        CouponModel coupon = couponService.getCoupon(couponId);

        return UserCouponInfo.from(userCoupon, coupon);
    }

    /**
     * 선착순 발급 "요청"을 접수한다. 여기서 발급하지 않고, 트랜잭션 안에서 요청 이벤트를 발행한 뒤
     * 추적용 requestId 를 돌려준다(202 Accepted 성격). 실제 발급은 컨슈머가 수행.
     * <p>
     * 이벤트는 {@code @Transactional} 안에서 발행되어 OutboxEventListener(BEFORE_COMMIT)가 outbox 행으로
     * 함께 커밋한다. 발행 실패가 접수를 롤백시키고(원자성), Relay가 acks=all+idempotence로 발행한다(유실/중복 방지).
     */
    @Transactional
    public String requestIssue(String loginId, String loginPw, Long couponId) {
        UserModel user = userService.getUser(loginId, loginPw);
        String requestId = UUID.randomUUID().toString();
        // 상태 기록(PENDING)과 발행 이벤트가 같은 트랜잭션 → outbox 행과 함께 원자적으로 커밋된다.
        couponService.recordPending(requestId, user.getId(), couponId);
        eventPublisher.publishEvent(new CouponIssueRequestedEvent(requestId, couponId, user.getId()));
        return requestId;
    }

    /** 발급 요청의 처리 상태를 조회한다(폴링). */
    public String getIssueStatus(String requestId) {
        return couponService.getIssueStatus(requestId);
    }

    public List<UserCouponInfo> getMyCoupons(String loginId, String loginPw) {
        UserModel user = userService.getUser(loginId, loginPw);
        List<UserCouponModel> userCoupons = couponService.getUserCoupons(user.getId());

        return userCoupons.stream()
            .map(uc -> UserCouponInfo.from(uc, couponService.getCoupon(uc.getCouponId())))
            .toList();
    }
}
