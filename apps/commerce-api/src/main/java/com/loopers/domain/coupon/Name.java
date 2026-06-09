package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record Name(
    @Column(name = "name", nullable = false, length = 100)
    String value
) {

    private static final int MIN_LENGTH = 1;
    private static final int MAX_LENGTH = 100;

    public static Name from(String value) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 이름은 필수입니다.");
        }

        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, String.format("쿠폰 이름은 %d~%d자만 허용됩니다.", MIN_LENGTH, MAX_LENGTH));
        }

        return new Name(value);
    }
}
