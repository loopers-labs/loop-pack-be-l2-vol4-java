package com.loopers.coupon.domain.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embeddable;

import java.time.ZonedDateTime;

@Embeddable
public record CouponExpiration(ZonedDateTime expiredAt) {

    public CouponExpiration {
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 만료일은 비어있을 수 없습니다.");
        }
    }

    public static CouponExpiration of(ZonedDateTime expiredAt) {
        return new CouponExpiration(expiredAt);
    }

    public boolean isExpiredAt(ZonedDateTime now) {
        if (now == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 시각은 비어있을 수 없습니다.");
        }
        return !now.isBefore(expiredAt);
    }
}
