package com.loopers.infrastructure.payment;

import com.loopers.interfaces.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * pg-simulator(:8082) HTTP 계약 미러. 응답은 pg-simulator의 ApiResponse&lt;T&gt; 래퍼로 오며,
 * commerce-api의 ApiResponse 레코드와 JSON 구조가 동일해 그대로 역직렬화한다.
 * <p>
 * 인증 헤더 {@code X-USER-ID} 필수. base-url은 {@code pg-simulator.base-url} 설정값.
 */
@FeignClient(name = "pg-simulator", url = "${pg-simulator.base-url:http://localhost:8082}")
public interface PgSimulatorFeignClient {

    /** 결제 요청 → 즉시 PENDING 거래 발급(transactionKey). 서버 불안정 시 500 발생(어댑터가 재시도). */
    @PostMapping("/api/v1/payments")
    ApiResponse<PgTransactionDto> requestPayment(
            @RequestHeader("X-USER-ID") String userId,
            @RequestBody PgPaymentRequestDto request
    );

    /** 주문별 거래 목록 (reconcile 진실원천). */
    @GetMapping("/api/v1/payments")
    ApiResponse<PgOrderDto> findTransactionsByOrder(
            @RequestHeader("X-USER-ID") String userId,
            @RequestParam("orderId") String orderId
    );

    /** 결제 요청 본문 — pg-simulator PaymentDto.PaymentRequest와 동일 필드. */
    record PgPaymentRequestDto(
            String orderId,
            String cardType,
            String cardNo,
            Long amount,
            String callbackUrl
    ) {
    }

    /** 거래 응답 — pg-simulator PaymentDto.TransactionResponse와 동일 필드. */
    record PgTransactionDto(
            String transactionKey,
            String status,
            String reason
    ) {
    }

    /** 주문별 응답 — pg-simulator PaymentDto.OrderResponse와 동일 필드. */
    record PgOrderDto(
            String orderId,
            java.util.List<PgTransactionDto> transactions
    ) {
    }
}
