package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public enum CardType {
    SAMSUNG, KB, HYUNDAI, SHINHAN, LOTTE, BC, NH;

    public static CardType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 종류는 비어있을 수 없습니다.");
        }
        try {
            return CardType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "지원하지 않는 카드 종류입니다: " + raw);
        }
    }
}
