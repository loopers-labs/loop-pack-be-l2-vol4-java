package com.loopers.domain.coupon.specification;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

@Component
public class UsableCouponSpecification implements Specification<CouponUseContext> {

    @Override
    public boolean isSatisfiedBy(CouponUseContext context) {
        return context != null
            && context.hasIssuedCoupon()
            && context.hasCouponTemplate()
            && context.isCouponIssuedToUser()
            && context.isCouponAvailable()
            && context.isCouponApplicableToOrder();
    }

    public void validateUsable(CouponUseContext context) {
        validateContext(context);
        validateUserCoupon(context.userCoupon());
        validateCouponTemplate(context.couponTemplate());
        validateOwner(context.userCoupon(), context.userId());
        validateAvailable(context.userCoupon());
        context.couponTemplate().validateApplicableTo(context.orderAmount(), context.now());
    }

    private static void validateContext(CouponUseContext context) {
        if (context == null) {
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
