package com.loopers.payment.interfaces.api;

import com.loopers.payment.application.PaymentInfo;
import com.loopers.payment.application.PaymentCallbackCommand;
import com.loopers.payment.application.RequestPaymentCommand;
import com.loopers.payment.domain.CardType;
import com.loopers.payment.domain.PaymentFailureReason;
import com.loopers.payment.domain.PaymentStatus;
import com.loopers.payment.domain.PgPaymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.ZonedDateTime;

public class PaymentV1Dto {

    public record PaymentRequest(
        @NotNull(message = "결제 주문 ID는 비어있을 수 없습니다.")
        Long orderId,

        @NotNull(message = "카드 타입은 비어있을 수 없습니다.")
        CardType cardType,

        @NotBlank(message = "카드 번호는 비어있을 수 없습니다.")
        String cardNo
    ) {

        public RequestPaymentCommand toCommand(Long userId) {
            return new RequestPaymentCommand(userId, orderId, cardType, cardNo);
        }
    }

    public record PaymentResponse(
        Long id,
        Long orderId,
        long amount,
        CardType cardType,
        String maskedCardNo,
        PaymentStatus status,
        PaymentFailureReason failureReason,
        ZonedDateTime requestedAt,
        ZonedDateTime completedAt
    ) {

        public static PaymentResponse from(PaymentInfo info) {
            return new PaymentResponse(
                info.id(),
                info.orderId(),
                info.amount(),
                info.cardType(),
                info.maskedCardNo(),
                info.status(),
                info.failureReason(),
                info.requestedAt(),
                info.completedAt()
            );
        }
    }

    public record PaymentCallbackRequest(
        @NotBlank(message = "PG 거래 키는 비어있을 수 없습니다.")
        String transactionKey,

        @NotNull(message = "결제 주문 ID는 비어있을 수 없습니다.")
        Long orderId,

        @NotNull(message = "카드 타입은 비어있을 수 없습니다.")
        CardType cardType,

        @NotNull(message = "결제 금액은 비어있을 수 없습니다.")
        @Positive(message = "결제 금액은 0보다 커야 합니다.")
        Long amount,

        @NotNull(message = "PG 거래 상태는 비어있을 수 없습니다.")
        PgPaymentStatus status,

        String reason
    ) {

        public PaymentCallbackCommand toCommand() {
            return new PaymentCallbackCommand(
                transactionKey,
                orderId,
                amount,
                cardType,
                status,
                toFailureReason(),
                reason
            );
        }

        private PaymentFailureReason toFailureReason() {
            if (status != PgPaymentStatus.FAILED) {
                return null;
            }
            if (reason == null) {
                return PaymentFailureReason.PG_TRANSACTION_FAILED;
            }
            if (reason.contains("한도")) {
                return PaymentFailureReason.LIMIT_EXCEEDED;
            }
            if (reason.contains("카드")) {
                return PaymentFailureReason.INVALID_CARD;
            }
            return PaymentFailureReason.PG_TRANSACTION_FAILED;
        }
    }
}
