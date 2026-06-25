package com.loopers.payment.interfaces.api;

import com.loopers.payment.application.PaymentInfo;
import com.loopers.payment.application.RequestPaymentCommand;
import com.loopers.payment.domain.CardType;
import com.loopers.payment.domain.PaymentFailureReason;
import com.loopers.payment.domain.PaymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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
}
