package com.loopers.application.payment.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public class PaymentCommand {
    public record Request(
        String userId,
        Long orderId,
        CardType cardType,
        String cardNo
    ) {
        private static final String CARD_NO_PATTERN = "^\\d{4}-\\d{4}-\\d{4}-\\d{4}$";

        public Request {
            if (userId == null || userId.isBlank()) {
                throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 필수입니다.");
            }
            if (orderId == null || orderId <= 0) {
                throw new CoreException(ErrorType.BAD_REQUEST, "주문 ID는 필수입니다.");
            }
            if (cardType == null) {
                throw new CoreException(ErrorType.BAD_REQUEST, "카드 타입은 필수입니다.");
            }
            if (cardNo == null || !cardNo.matches(CARD_NO_PATTERN)) {
                throw new CoreException(ErrorType.BAD_REQUEST, "카드 번호는 xxxx-xxxx-xxxx-xxxx 형식이어야 합니다.");
            }
        }
    }

    public enum CardType {
        SAMSUNG,
        KB,
        HYUNDAI
    }

    public record Callback(
        String transactionKey,
        String orderId,
        CardType cardType,
        String cardNo,
        Long amount,
        TransactionStatus status,
        String reason
    ) {
        public Callback {
            if (transactionKey == null || transactionKey.isBlank()) {
                throw new CoreException(ErrorType.BAD_REQUEST, "거래 키는 필수입니다.");
            }
            if (orderId == null || orderId.isBlank()) {
                throw new CoreException(ErrorType.BAD_REQUEST, "PG 주문 ID는 필수입니다.");
            }
            if (status == null) {
                throw new CoreException(ErrorType.BAD_REQUEST, "PG 결제 상태는 필수입니다.");
            }
        }
    }

    public enum TransactionStatus {
        PENDING,
        SUCCESS,
        FAILED;

        public com.loopers.domain.payment.gateway.PaymentGatewayTransactionStatus toGatewayStatus() {
            return switch (this) {
                case PENDING -> com.loopers.domain.payment.gateway.PaymentGatewayTransactionStatus.PENDING;
                case SUCCESS -> com.loopers.domain.payment.gateway.PaymentGatewayTransactionStatus.SUCCESS;
                case FAILED -> com.loopers.domain.payment.gateway.PaymentGatewayTransactionStatus.FAILED;
            };
        }
    }
}
