package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.UserCoupon;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 쿠폰 유스케이스 조립.
 * getMyCoupons 는 N개의 UserCoupon + N개의 Coupon 조회를 하나의 readOnly 트랜잭션으로 묶는다.
 * (Service 들의 @Transactional 은 REQUIRED 로 합류하여 단일 트랜잭션이 된다)
 */
@RequiredArgsConstructor
@Component
public class CouponFacade {

    private final CouponService couponService;

    /**
     * 쿠폰 발급. Service 의 @Transactional 로 동작.
     * 응답을 위해 발급된 UserCoupon + 템플릿 Coupon 을 조합한다.
     */
    public UserCouponInfo issueCoupon(Long userId, Long couponId) {
        UserCoupon userCoupon = couponService.issueCoupon(userId, couponId);
        Coupon coupon = couponService.getCoupon(couponId);
        return UserCouponInfo.from(userCoupon, coupon);
    }

    /**
     * 내 쿠폰 목록. Service 호출을 묶어 단일 readOnly 트랜잭션으로 처리.
     */
    @Transactional(readOnly = true)
    public List<UserCouponInfo> getMyCoupons(Long userId) {
        List<UserCoupon> userCoupons = couponService.getMyCoupons(userId);
        if (userCoupons.isEmpty()) {
            return List.of();
        }
        // Coupon 템플릿을 N+1 없이 한 번에 모으기 위해 distinct 한 id 들에 대해 조회
        Map<Long, Coupon> couponMap = userCoupons.stream()
            .map(UserCoupon::getCouponId)
            .distinct()
            .map(couponService::getCoupon)
            .collect(Collectors.toMap(Coupon::getId, c -> c));

        return userCoupons.stream()
            .map(uc -> UserCouponInfo.from(uc, couponMap.get(uc.getCouponId())))
            .toList();
    }
}
