package com.loopers.domain.coupon;

import java.time.ZonedDateTime;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record ExpiredAt(
    @Column(name = "expired_at", nullable = false)
    ZonedDateTime value
) {

    public static ExpiredAt of(ZonedDateTime value, ZonedDateTime now) {
        if (value == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료 시각은 필수입니다.");
        }

        if (value.isBefore(now)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료 시각은 현재 시각 이후여야 합니다.");
        }

        return new ExpiredAt(value);
    }
}
