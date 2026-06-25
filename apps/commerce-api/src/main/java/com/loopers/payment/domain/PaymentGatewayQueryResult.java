package com.loopers.payment.domain;

import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;

public record PaymentGatewayQueryResult<T>(
    PaymentGatewayQueryStatus status,
    T data,
    PaymentFailureReason failureReason,
    String reason
) {

    public PaymentGatewayQueryResult {
        if (status == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 게이트웨이 조회 상태는 비어있을 수 없습니다.");
        }
        switch (status) {
            case FOUND -> validateFound(data, failureReason);
            case NOT_FOUND, FAILED, UNKNOWN -> validateNotFound(data, failureReason);
        }
    }

    public static <T> PaymentGatewayQueryResult<T> found(T data) {
        return new PaymentGatewayQueryResult<>(PaymentGatewayQueryStatus.FOUND, data, null, null);
    }

    public static <T> PaymentGatewayQueryResult<T> notFound(String reason) {
        return new PaymentGatewayQueryResult<>(
            PaymentGatewayQueryStatus.NOT_FOUND,
            null,
            PaymentFailureReason.PG_TRANSACTION_NOT_FOUND,
            reason
        );
    }

    public static <T> PaymentGatewayQueryResult<T> failed(PaymentFailureReason failureReason, String reason) {
        return new PaymentGatewayQueryResult<>(PaymentGatewayQueryStatus.FAILED, null, failureReason, reason);
    }

    public static <T> PaymentGatewayQueryResult<T> unknown(PaymentFailureReason failureReason, String reason) {
        return new PaymentGatewayQueryResult<>(PaymentGatewayQueryStatus.UNKNOWN, null, failureReason, reason);
    }

    public boolean isFound() {
        return status == PaymentGatewayQueryStatus.FOUND;
    }

    private static void validateFound(Object data, PaymentFailureReason failureReason) {
        if (data == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "조회된 결제 게이트웨이 결과에는 데이터가 필요합니다.");
        }
        if (failureReason != null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "조회된 결제 게이트웨이 결과에는 실패 사유가 없어야 합니다.");
        }
    }

    private static void validateNotFound(Object data, PaymentFailureReason failureReason) {
        if (data != null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "조회 실패 결제 게이트웨이 결과에는 데이터가 없어야 합니다.");
        }
        if (failureReason == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "조회 실패 결제 게이트웨이 결과에는 실패 사유가 필요합니다.");
        }
    }
}
