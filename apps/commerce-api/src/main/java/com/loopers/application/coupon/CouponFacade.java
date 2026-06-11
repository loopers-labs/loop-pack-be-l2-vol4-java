package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class CouponFacade {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final UserRepository userRepository;

    @Transactional
    public UserCouponInfo issueCoupon(String loginId, Long couponId) {
        UserModel user = loadUser(loginId);
        Coupon coupon = couponRepository.find(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + couponId + "] 쿠폰을 찾을 수 없습니다."));
        ZonedDateTime now = ZonedDateTime.now();
        UserCoupon issued = userCouponRepository.save(coupon.issueTo(user.getId(), now));
        return UserCouponInfo.from(issued, now);
    }

    @Transactional(readOnly = true)
    public List<UserCouponInfo> getMyCoupons(String loginId) {
        UserModel user = loadUser(loginId);
        ZonedDateTime now = ZonedDateTime.now();
        return userCouponRepository.findAllByUserId(user.getId()).stream()
            .map(userCoupon -> UserCouponInfo.from(userCoupon, now))
            .toList();
    }

    private UserModel loadUser(String loginId) {
        return userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다."));
    }
}
