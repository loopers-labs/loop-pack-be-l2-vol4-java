package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentCriteria;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PgStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.regex.Pattern;

public class PaymentV1Dto {

    private static final Pattern CARD_NO = Pattern.compile("^\\d{4}-\\d{4}-\\d{4}-\\d{4}$");

    // ===== 결제 요청 =====
    public record PaymentRequest(Long orderId, CardType cardType, String cardNo) {

        /** 헤더 userId 와 합쳐 응용 입력으로 변환. PG 호출 전에 형식을 미리 검증해 4xx 를 예방. */
        public PaymentCriteria toCriteria(Long userId) {
            if (orderId == null) {
                throw new CoreException(ErrorType.BAD_REQUEST, "orderId 는 필수입니다.");
            }
            if (cardType == null) {
                throw new CoreException(ErrorType.BAD_REQUEST, "cardType 은 필수입니다.");
            }
            if (cardNo == null || !CARD_NO.matcher(cardNo).matches()) {
                throw new CoreException(ErrorType.BAD_REQUEST, "cardNo 는 xxxx-xxxx-xxxx-xxxx 형식이어야 합니다.");
            }
            return new PaymentCriteria(userId, orderId, cardType, cardNo);
        }
    }

    // ===== 결제 응답 =====
    public record PaymentResponse(
            Long id,
            Long orderId,
            String status,
            String transactionKey,
            String reason,
            long amount
    ) {
        public static PaymentResponse from(PaymentInfo info) {
            return new PaymentResponse(
                    info.id(), info.orderId(), info.status().name(),
                    info.transactionKey(), info.reason(), info.amount());
        }
    }

    // ===== PG 콜백 바디 (pg-simulator 의 TransactionInfo 와 동일) =====
    public record CallbackRequest(
            String transactionKey,
            String orderId,
            String cardType,
            String cardNo,
            Long amount,
            String status,     // PENDING / SUCCESS / FAILED
            String reason
    ) {
        public PgStatus toPgStatus() {
            try {
                return PgStatus.valueOf(status);
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new CoreException(ErrorType.BAD_REQUEST, "알 수 없는 콜백 상태: " + status);
            }
        }
    }
}
