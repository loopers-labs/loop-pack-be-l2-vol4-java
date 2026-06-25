package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentCommand;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.regex.Pattern;

public class PaymentV1Dto {

    private static final Pattern CARD_NO_PATTERN = Pattern.compile("^\\d{4}-\\d{4}-\\d{4}-\\d{4}$");

    public record PayRequest(
            Long orderId, 
            CardType cardType, 
            String cardNo
    ) {
        public PaymentCommand toCommand() {
            if (orderId == null) {
                throw new CoreException(ErrorType.BAD_REQUEST, "주문 ID 는 필수입니다.");
            }
            if (cardType == null) {
                throw new CoreException(ErrorType.BAD_REQUEST, "카드 종류는 필수입니다.");
            }
            if (cardNo == null || !CARD_NO_PATTERN.matcher(cardNo).matches()) {
                throw new CoreException(ErrorType.BAD_REQUEST, "카드 번호는 xxxx-xxxx-xxxx-xxxx 형식이어야 합니다.");
            }
            return PaymentCommand.of(orderId, cardType, cardNo);
        }
    }

    public record PaymentResponse(
            Long orderId, 
            PaymentStatus status, 
            String transactionKey
    ) {
        public static PaymentResponse from(PaymentInfo info) {
            return new PaymentResponse(info.orderId(), info.status(), info.transactionKey());
        }
    }

    /** PG가 8080 으로 보내는 raw 콜백 바디(ApiResponse 래퍼 없음). */
    public record CallbackRequest(
            String transactionKey,
            String orderId,
            CardType cardType,
            String cardNo,
            Long amount,
            PaymentStatus status,
            String reason
    ) {
    }
}