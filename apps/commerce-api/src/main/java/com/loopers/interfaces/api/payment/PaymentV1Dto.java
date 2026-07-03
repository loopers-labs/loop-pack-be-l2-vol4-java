package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.payment.CardType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public final class PaymentV1Dto {

    private PaymentV1Dto() {
    }

    /**
     * 결제 요청. amount 는 클라이언트가 보내지 않는다(서버가 order.finalPrice 에서 도출 — 금액 위변조 방지).
     * orderId 는 우리 주문의 orderNumber 다.
     */
    public record PaymentRequest(
            @NotBlank(message = "주문 번호는 필수입니다.") String orderId,
            @NotNull(message = "카드 종류는 필수입니다.") CardType cardType,
            @NotBlank(message = "카드 번호는 필수입니다.")
            @Pattern(regexp = "^\\d{4}-\\d{4}-\\d{4}-\\d{4}$", message = "카드 번호는 xxxx-xxxx-xxxx-xxxx 형식이어야 합니다.")
            String cardNo
    ) {
    }

    /** 결제 요청 응답(202). 최종 결과는 클라이언트가 조회 API 로 폴링한다. */
    public record PaymentResponse(Long paymentId, String orderNumber, String status, String message) {
        public static PaymentResponse from(PaymentInfo info) {
            return new PaymentResponse(info.paymentId(), info.orderNumber(), info.status().name(), info.message());
        }
    }

    /** 결제 조회 응답(클라이언트 폴링용). 확정된 DB 상태를 그대로 노출. */
    public record PaymentStatusResponse(Long paymentId, String orderNumber, String status, String failureReason) {
        public static PaymentStatusResponse from(PaymentInfo info) {
            return new PaymentStatusResponse(info.paymentId(), info.orderNumber(), info.status().name(), info.failureReason());
        }
    }

    /**
     * PG 콜백 페이로드. pg-simulator 의 PaymentCoreRelay 가 TransactionInfo 전체를 전송하므로
     * 필드 전체를 받아 역직렬화 실패를 막는다. status 는 PG enum(PENDING/SUCCESS/FAILED) 문자열이다.
     * amount·cardNo 는 무결성 가드(설계 §6.2)에 사용한다.
     */
    public record CallbackRequest(
            String transactionKey,
            String orderId,
            String cardType,
            String cardNo,
            Long amount,
            String status,
            String reason
    ) {
    }

    /** 수동 복구 결과. outcome 은 PAID/FAILED/UNREACHED_FAILED/STILL_PROCESSING/ISOLATED 중 하나. */
    public record ReconcileResponse(Long paymentId, String outcome) {
    }
}
