package com.loopers.domain.coupon;

import com.loopers.domain.coupon.enums.UserCouponStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

@Component
public class CouponUsePolicy {

    public void validate(UserCouponModel userCoupon, Long requestUserId) {
        if (!userCoupon.getUserId().equals(requestUserId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "본인 소유의 쿠폰이 아닙니다.");
        }
        if (userCoupon.getStatus() != UserCouponStatus.ISSUED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 사용된 쿠폰입니다.");
        }
        if (userCoupon.getCoupon().isExpired()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }
    }
}
