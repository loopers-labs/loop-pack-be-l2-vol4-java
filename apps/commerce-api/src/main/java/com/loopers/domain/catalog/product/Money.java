package com.loopers.domain.catalog.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public class Money {

    private final Long amount;

    public Money(Long amount) {
        if (amount == null || amount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0 이상이어야 합니다.");
        }

        this.amount = amount;
    }

    public Long getAmount() {
        return amount;
    }
}
