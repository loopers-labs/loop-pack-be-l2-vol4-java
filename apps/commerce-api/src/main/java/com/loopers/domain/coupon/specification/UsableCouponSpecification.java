package com.loopers.domain.coupon.specification;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

@Component
public class UsableCouponSpecification implements Specification<CouponUseAttempt> {

    @Override
    public boolean isSatisfiedBy(CouponUseAttempt attempt) {
        return attempt != null
            && attempt.hasIssuedCoupon()
            && attempt.hasCouponTemplate()
            && attempt.isIssuedToUser()
            && attempt.isAvailable()
            && attempt.isApplicableToOrder();
    }

    public void validateUsable(CouponUseAttempt attempt) {
        validateAttempt(attempt);
        validateUserCoupon(attempt.userCoupon());
        validateCouponTemplate(attempt.couponTemplate());
        validateOwner(attempt.userCoupon(), attempt.userId());
        validateAvailable(attempt.userCoupon());
        attempt.couponTemplate().validateApplicableTo(attempt.orderAmount(), attempt.now());
    }

    private static void validateAttempt(CouponUseAttempt attempt) {
        if (attempt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 사용 조건은 비어있을 수 없습니다.");
        }
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
