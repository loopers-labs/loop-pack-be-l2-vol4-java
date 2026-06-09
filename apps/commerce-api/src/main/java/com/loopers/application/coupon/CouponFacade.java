package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class CouponFacade {

    private final UserService userService;
    private final CouponService couponService;

    public UserCouponInfo issue(String loginId, String loginPw, Long couponId) {
        UserModel user = userService.getUser(loginId, loginPw);
        UserCouponModel userCoupon = couponService.issueCoupon(user.getId(), couponId);
        CouponModel coupon = couponService.getCoupon(couponId);
        
        return UserCouponInfo.from(userCoupon, coupon);
    }
}
