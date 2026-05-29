package com.loopers.domain.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class Money {

    @Column(name = "amount", nullable = false)
    private Long amount;

    protected Money() {}

    public Money(Long amount) {
        if (amount == null || amount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST);
        }
        this.amount = amount;
    }

    public Long getAmount() {
        return amount;
    }
}
