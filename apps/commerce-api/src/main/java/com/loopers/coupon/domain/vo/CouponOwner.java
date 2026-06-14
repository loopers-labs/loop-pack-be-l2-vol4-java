package com.loopers.coupon.domain.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embeddable;

@Embeddable
public record CouponOwner(Long userId) {

    public CouponOwner {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 비어있을 수 없습니다.");
        }
    }

    public static CouponOwner of(Long userId) {
        return new CouponOwner(userId);
    }

    public boolean isSameUser(Long userId) {
        return this.userId.equals(userId);
    }
}
