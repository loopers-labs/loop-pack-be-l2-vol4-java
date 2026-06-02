package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public class DuplicateCouponIssueException extends CoreException {

    public DuplicateCouponIssueException(Throwable cause) {
        super(ErrorType.CONFLICT, "이미 발급된 쿠폰입니다.");
        initCause(cause);
    }
}
