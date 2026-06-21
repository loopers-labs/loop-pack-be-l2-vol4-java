package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.payment.PaymentCommand;
import com.loopers.application.payment.payment.PaymentResult;
import com.loopers.domain.payment.payment.PaymentStatus;

public class PaymentDto {
    public record PaymentRequest(
        Long orderId,
        CardType cardType,
        String cardNo
    ) {
        public PaymentCommand.Request toCommand(String userId) {
            return new PaymentCommand.Request(
                userId,
                orderId,
                cardType == null ? null : cardType.toCommand(),
                cardNo
            );
        }
    }

    public record PaymentCallbackRequest(
        String transactionKey,
        String orderId,
        CardType cardType,
        String cardNo,
        Long amount,
        TransactionStatus status,
        String reason
    ) {
        public PaymentCommand.Callback toCommand() {
            return new PaymentCommand.Callback(
                transactionKey,
                orderId,
                cardType == null ? null : cardType.toCommand(),
                cardNo,
                amount,
                status == null ? null : status.toCommand(),
                reason
            );
        }
    }

    public record PaymentResponse(
        Long orderId,
        PaymentStatus paymentStatus,
        String transactionKey,
        String message
    ) {
        public static PaymentResponse from(PaymentResult.Request result) {
            return new PaymentResponse(
                result.orderId(),
                result.paymentStatus(),
                result.transactionKey(),
                result.message()
            );
        }
    }

    public enum CardType {
        SAMSUNG,
        KB,
        HYUNDAI;

        private PaymentCommand.CardType toCommand() {
            return switch (this) {
                case SAMSUNG -> PaymentCommand.CardType.SAMSUNG;
                case KB -> PaymentCommand.CardType.KB;
                case HYUNDAI -> PaymentCommand.CardType.HYUNDAI;
            };
        }
    }

    public enum TransactionStatus {
        PENDING,
        SUCCESS,
        FAILED;

        private PaymentCommand.TransactionStatus toCommand() {
            return switch (this) {
                case PENDING -> PaymentCommand.TransactionStatus.PENDING;
                case SUCCESS -> PaymentCommand.TransactionStatus.SUCCESS;
                case FAILED -> PaymentCommand.TransactionStatus.FAILED;
            };
        }
    }
}
