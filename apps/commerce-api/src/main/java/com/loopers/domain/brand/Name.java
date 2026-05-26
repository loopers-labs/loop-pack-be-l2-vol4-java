package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record Name(
    @Column(name = "name", nullable = false, length = 50)
    String value
) {

    private static final int MIN_LENGTH = 1;
    private static final int MAX_LENGTH = 50;

    public static Name from(String value) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 이름은 필수입니다.");
        }

        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, String.format("브랜드 이름은 %d~%d자만 허용됩니다.", MIN_LENGTH, MAX_LENGTH));
        }

        return new Name(value);
    }
}
