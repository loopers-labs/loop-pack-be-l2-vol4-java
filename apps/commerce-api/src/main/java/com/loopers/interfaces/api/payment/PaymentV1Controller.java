package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentCriteria;
import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 결제 API.
 * <p>
 * 본 프로젝트는 1주차 User 도메인이 main 에 없는 상태라, 인증 헤더는 단순화해서
 * {@code X-Loopers-User-Id} 로 받는다. 운영 환경의 {@code X-Loopers-LoginId / -LoginPw}
 * 인증은 본 PR 범위 밖.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentV1Controller {

    private final PaymentFacade paymentFacade;

    @PostMapping
    public ApiResponse<PaymentV1Dto.PaymentResponse> request(
        @RequestHeader("X-Loopers-User-Id") Long userId,
        @RequestBody PaymentV1Dto.RequestPayment request
    ) {
        PaymentInfo info = paymentFacade.request(new PaymentCriteria.Request(
            userId,
            request.orderId(),
            request.cardType(),
            request.cardNo()
        ));
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(info));
    }

    @GetMapping("/by-order/{orderId}")
    public ApiResponse<PaymentV1Dto.PaymentResponse> getByOrderId(
        @PathVariable("orderId") Long orderId
    ) {
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(paymentFacade.getByOrderId(orderId)));
    }

    /**
     * PG 콜백 수신. 멱등 보장 — 같은 콜백이 재전송되어도 안전.
     * 본 단계에선 서명 검증 없음 (시뮬레이터 가정). 운영 단계에선 HMAC 검증 추가 필요.
     */
    @PostMapping("/callback")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> callback(@RequestBody PaymentV1Dto.Callback callback) {
        paymentFacade.handleCallback(new PaymentCriteria.Callback(
            callback.transactionKey(),
            callback.status(),
            callback.reason()
        ));
        return ApiResponse.success(null);
    }
}
