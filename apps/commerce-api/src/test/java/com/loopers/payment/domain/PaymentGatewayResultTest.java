package com.loopers.payment.domain;

import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class PaymentGatewayResultTest {

    private static final PaymentGatewayTransaction TRANSACTION = new PaymentGatewayTransaction(
        "20250816:TR:9577c5",
        PgPaymentStatus.PENDING,
        null
    );

    @DisplayName("접수된 PG 요청 결과는 PG 거래 정보가 있어야 한다.")
    @Test
    void requiresTransaction_whenRequestAccepted() {
        // act & assert
        assertThatThrownBy(() -> PaymentGatewayResult.accepted(null))
            .isInstanceOf(CoreException.class)
            .extracting("errorType").isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("실패한 PG 요청 결과는 요청 실패 사유만 가질 수 있다.")
    @Test
    void rejectsPaymentFailureReason_whenRequestFailed() {
        // act & assert
        assertThatThrownBy(() -> PaymentGatewayResult.failed(PaymentFailureReason.LIMIT_EXCEEDED, "한도 초과"))
            .isInstanceOf(CoreException.class)
            .extracting("errorType").isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("확인이 필요한 PG 요청 결과는 확인 필요 사유만 가질 수 있다.")
    @Test
    void rejectsRequestFailureReason_whenRequestUnknown() {
        // act & assert
        assertThatThrownBy(() -> PaymentGatewayResult.unknown(PaymentFailureReason.PG_REQUEST_FAILED, "요청 실패"))
            .isInstanceOf(CoreException.class)
            .extracting("errorType").isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("접수되지 않았거나 확인이 필요한 PG 요청 결과는 PG 거래 정보를 가질 수 없다.")
    @Test
    void rejectsTransaction_whenRequestWasNotAccepted() {
        // act & assert
        assertAll(
            () -> assertThatThrownBy(() -> new PaymentGatewayResult(
                PaymentGatewayRequestStatus.FAILED,
                TRANSACTION,
                PaymentFailureReason.PG_REQUEST_FAILED,
                "요청 실패"
            ))
                .isInstanceOf(CoreException.class)
                .extracting("errorType").isEqualTo(ErrorType.BAD_REQUEST),
            () -> assertThatThrownBy(() -> new PaymentGatewayResult(
                PaymentGatewayRequestStatus.UNKNOWN,
                TRANSACTION,
                PaymentFailureReason.PG_TIMEOUT,
                "응답 유실"
            ))
                .isInstanceOf(CoreException.class)
                .extracting("errorType").isEqualTo(ErrorType.BAD_REQUEST)
        );
    }
}
