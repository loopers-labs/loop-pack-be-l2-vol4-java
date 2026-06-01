package com.loopers.domain.coupon.specification;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

@Component
public class UsableCouponSpecification implements Specification<CouponUseAttempt> {

    @Override
    public boolean isSatisfiedBy(CouponUseAttempt attempt) {
        return attempt != null && attempt.canUse();
    }

    public void confirmUsable(CouponUseAttempt attempt) {
        confirm(attempt != null, ErrorType.BAD_REQUEST, "쿠폰 사용 조건은 비어있을 수 없습니다.");
        attempt.confirmUsable();
    }

    private static void confirm(boolean condition, ErrorType errorType, String message) {
        if (!condition) {
            throw new CoreException(errorType, message);
        }
    }
}
