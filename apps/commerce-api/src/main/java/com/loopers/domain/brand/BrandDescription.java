package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embeddable;

@Embeddable
public record BrandDescription(String value) {

    public BrandDescription {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 설명은 비어있을 수 없습니다.");
        }
    }

    public static BrandDescription of(String value) {
        return new BrandDescription(value);
    }
}