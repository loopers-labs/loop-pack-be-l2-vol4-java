package com.loopers.common.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Money {

    public static final Money ZERO = new Money(0L);

    private long value;

    private Money(long value) {
        if (value < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "금액은 0 이상이어야 합니다.");
        }
        this.value = value;
    }

    public static Money of(long value) {
        return new Money(value);
    }

    public Money plus(Money other) {
        return new Money(this.value + other.value);
    }

    public Money times(int quantity) {
        return new Money(this.value * quantity);
    }

    public long value() {
        return value;
    }
}
