package com.loopers.domain.user.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embeddable;

import java.time.LocalDate;

@Embeddable
public record BirthDate(LocalDate value) {

    public BirthDate {
        if (value == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 필수입니다.");
        }
    }
}
