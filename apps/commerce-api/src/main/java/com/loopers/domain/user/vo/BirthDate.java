package com.loopers.domain.user.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embeddable;

import java.time.LocalDate;

@Embeddable
public record BirthDate(LocalDate value) {

    public BirthDate {
        if (value == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 비어있을 수 없습니다.");
        }
    }

    public static BirthDate of(LocalDate value) {
        return new BirthDate(value);
    }
}
