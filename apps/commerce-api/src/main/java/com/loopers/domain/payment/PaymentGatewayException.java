package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.Getter;

@Getter
public class PaymentGatewayException extends CoreException {

    private final FailureReason failureReason;

    public PaymentGatewayException(FailureReason failureReason, String customMessage) {
        super(ErrorType.INTERNAL_ERROR, customMessage);
        this.failureReason = failureReason;
    }

    public enum FailureReason {
        EMPTY_RESPONSE,
        DECODE_FAILED,
        RETRY_FAILED,
        UNKNOWN
    }
}
