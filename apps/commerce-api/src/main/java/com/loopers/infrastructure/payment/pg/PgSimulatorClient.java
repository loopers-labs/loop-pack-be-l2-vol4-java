package com.loopers.infrastructure.payment.pg;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * pg-simulator HTTP 클라이언트.
 *
 * <p>pg-simulator 는 모든 응답을 {@link PgPaymentDto.PgApiResponse} envelope 으로 감싼다.
 * 따라서 반환 타입은 envelope 이며, 호출부에서 {@code .data()} 로 실제 페이로드를 꺼낸다.
 */
@FeignClient(name = "pg-simulator", url = "${pg.base-url}")
public interface PgSimulatorClient {

    @PostMapping("/api/v1/payments")
    PgPaymentDto.PgApiResponse<PgPaymentDto.TransactionResponse> request(
        @RequestHeader("X-USER-ID") String userId,
        @RequestBody PgPaymentDto.PaymentRequest request
    );

    @GetMapping("/api/v1/payments")
    PgPaymentDto.PgApiResponse<PgPaymentDto.OrderTransactionResponse> getByOrderId(
        @RequestHeader("X-USER-ID") String userId,
        @RequestParam("orderId") String orderId
    );
}
