package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BirthDate {

    @Column(name = "birth_date", nullable = false)
    private LocalDate value;

    public BirthDate(LocalDate value) {
        if (value == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 비어있을 수 없습니다.");
        }
        if (value.isAfter(LocalDate.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 미래일 수 없습니다.");
        }
        this.value = value;
    }
}
