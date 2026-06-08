package com.loopers.domain.payment.enums;

public enum PaymentStatus {
    PENDING("결제 대기"),
    APPROVED("결제 승인"),
    FAILED("결제 실패"),
    EXPIRED("결제 만료");

    private final String description;

    PaymentStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
