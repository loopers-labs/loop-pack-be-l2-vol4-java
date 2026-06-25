package com.loopers.payment.domain;

import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;

import java.util.EnumSet;
import java.util.Set;

public record PaymentGatewayResult(
    PaymentGatewayRequestStatus status,
    PaymentGatewayTransaction transaction,
    PaymentFailureReason failureReason,
    String reason
) {

    private static final Set<PaymentFailureReason> REQUEST_FAILURE_REASONS = EnumSet.of(
        PaymentFailureReason.PG_REQUEST_FAILED,
        PaymentFailureReason.PG_UNAVAILABLE
    );
    private static final Set<PaymentFailureReason> UNKNOWN_REASONS = EnumSet.of(
        PaymentFailureReason.PG_TIMEOUT,
        PaymentFailureReason.UNKNOWN
    );

    public PaymentGatewayResult {
        if (status == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 게이트웨이 요청 상태는 비어있을 수 없습니다.");
        }
        switch (status) {
            case SUCCEEDED -> validateSucceeded(transaction, failureReason);
            case FAILED -> validateFailed(transaction, failureReason);
            case UNKNOWN -> validateUnknown(transaction, failureReason);
        }
    }

    public static PaymentGatewayResult succeeded(PaymentGatewayTransaction transaction) {
        return new PaymentGatewayResult(PaymentGatewayRequestStatus.SUCCEEDED, transaction, null, null);
    }

    public static PaymentGatewayResult failed(PaymentFailureReason failureReason, String reason) {
        return new PaymentGatewayResult(PaymentGatewayRequestStatus.FAILED, null, failureReason, reason);
    }

    public static PaymentGatewayResult unknown(PaymentFailureReason failureReason, String reason) {
        return new PaymentGatewayResult(PaymentGatewayRequestStatus.UNKNOWN, null, failureReason, reason);
    }

    public boolean isRequestSucceeded() {
        return status == PaymentGatewayRequestStatus.SUCCEEDED;
    }

    private static void validateSucceeded(
        PaymentGatewayTransaction transaction,
        PaymentFailureReason failureReason
    ) {
        if (transaction == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "성공한 결제 게이트웨이 요청에는 거래 정보가 필요합니다.");
        }
        if (failureReason != null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "성공한 결제 게이트웨이 요청에는 실패 사유가 없어야 합니다.");
        }
    }

    private static void validateFailed(
        PaymentGatewayTransaction transaction,
        PaymentFailureReason failureReason
    ) {
        validateNoTransaction(transaction);
        validateFailureReason(failureReason);
        if (!REQUEST_FAILURE_REASONS.contains(failureReason)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "실패한 결제 게이트웨이 요청에는 요청 실패 사유만 사용할 수 있습니다.");
        }
    }

    private static void validateUnknown(
        PaymentGatewayTransaction transaction,
        PaymentFailureReason failureReason
    ) {
        validateNoTransaction(transaction);
        validateFailureReason(failureReason);
        if (!UNKNOWN_REASONS.contains(failureReason)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "알 수 없는 결제 게이트웨이 요청에는 확인 필요 사유만 사용할 수 있습니다.");
        }
    }

    private static void validateNoTransaction(PaymentGatewayTransaction transaction) {
        if (transaction != null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "성공하지 않은 결제 게이트웨이 요청에는 거래 정보가 없어야 합니다.");
        }
    }

    private static void validateFailureReason(PaymentFailureReason failureReason) {
        if (failureReason == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "성공하지 않은 결제 게이트웨이 요청에는 실패 사유가 필요합니다.");
        }
    }
}
