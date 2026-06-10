package com.loopers.domain.coupon;

import com.loopers.support.Guard;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class CouponExpiry {

    @Column(nullable = false)
    private ZonedDateTime expiredAt;

    public CouponExpiry(ZonedDateTime expiredAt) {
        Guard.notNull(expiredAt, "쿠폰 만료일은 필수입니다.");
        this.expiredAt = expiredAt;
    }

    public boolean isExpired() {
        return ZonedDateTime.now().isAfter(expiredAt);
    }

    public CouponExpiry extend(ZonedDateTime newExpiredAt) {
        Guard.notNull(newExpiredAt, "만료일은 필수입니다.");
        if (!newExpiredAt.isAfter(expiredAt)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "새 만료일은 현재 만료일 이후여야 합니다.");
        }
        return new CouponExpiry(newExpiredAt);
    }
}
