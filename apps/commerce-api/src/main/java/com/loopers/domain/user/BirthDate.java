package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

@Embeddable
public class BirthDate {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Column(name = "birth_date", nullable = false)
    private LocalDate value;

    protected BirthDate() {}

    private BirthDate(LocalDate value) {
        this.value = value;
    }

    public static BirthDate of(LocalDate value) {
        if (value == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 비어있을 수 없습니다.");
        }
        if (value.isAfter(LocalDate.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 미래일 수 없습니다.");
        }
        return new BirthDate(value);
    }

    public LocalDate getValue() {
        return value;
    }

    public String formatAsYyyyMmDd() {
        return value.format(YYYYMMDD);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BirthDate other)) return false;
        return Objects.equals(this.value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
