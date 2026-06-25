package com.loopers.payment.domain;

import com.loopers.support.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements ErrorCode {
    PAYMENT_ORDER_NOT_FOUND("PAYMENT_ORDER_NOT_FOUND", "결제 대상 주문을 찾을 수 없습니다."),
    PAYMENT_ORDER_NOT_PAYABLE("PAYMENT_ORDER_NOT_PAYABLE", "결제할 수 없는 주문 상태입니다."),
    PAYMENT_ALREADY_IN_PROGRESS("PAYMENT_ALREADY_IN_PROGRESS", "이미 진행 중인 결제가 있습니다."),
    PAYMENT_CALLBACK_INVALID("PAYMENT_CALLBACK_INVALID", "콜백 정보가 결제와 일치하지 않습니다.");

    private final String code;
    private final String message;
}
