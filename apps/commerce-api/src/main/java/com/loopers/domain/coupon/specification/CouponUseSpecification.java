package com.loopers.domain.coupon.specification;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.vo.CouponMoney;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

@Component
public class CouponUseSpecification {

    public boolean isSatisfiedBy(
        UserCoupon userCoupon,
        CouponTemplate couponTemplate,
        Long userId,
        CouponMoney orderAmount,
        ZonedDateTime now
    ) {
        return userCoupon != null
            && couponTemplate != null
            && userCoupon.isIssuedTo(userId)
            && userCoupon.isAvailable()
            && couponTemplate.canApplyTo(orderAmount, now);
    }

    public void validateSatisfiedBy(
        UserCoupon userCoupon,
        CouponTemplate couponTemplate,
        Long userId,
        CouponMoney orderAmount,
        ZonedDateTime now
    ) {
        validateUserCoupon(userCoupon);
        validateCouponTemplate(couponTemplate);
        validateOwner(userCoupon, userId);
        validateAvailable(userCoupon);
        couponTemplate.validateApplicableTo(orderAmount, now);
    }

    private static void validateUserCoupon(UserCoupon userCoupon) {
        if (userCoupon == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저 쿠폰은 비어있을 수 없습니다.");
        }
    }

    private static void validateCouponTemplate(CouponTemplate couponTemplate) {
        if (couponTemplate == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 템플릿은 비어있을 수 없습니다.");
        }
    }

    private static void validateOwner(UserCoupon userCoupon, Long userId) {
        if (!userCoupon.isIssuedTo(userId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "다른 사용자의 쿠폰은 사용할 수 없습니다.");
        }
    }

    private static void validateAvailable(UserCoupon userCoupon) {
        if (!userCoupon.isAvailable()) {
            throw new CoreException(ErrorType.CONFLICT, "사용할 수 없는 쿠폰입니다.");
        }
    }
}
