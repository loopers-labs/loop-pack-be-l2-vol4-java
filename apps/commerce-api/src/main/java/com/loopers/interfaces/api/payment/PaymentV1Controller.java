package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 결제 — 주문 생성과 분리된 별도 진입점(03 §3.6). 사용자 식별은 OrderV1Controller와 동일하게
 * X-Loopers-LoginId/LoginPw 헤더로 처리한다. 인증 자체는 {@link PaymentFacade#pay}가 내부에서 수행하므로
 * 컨트롤러는 헤더를 그대로 위임한다.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentV1Controller {

    private final PaymentFacade paymentFacade;

    @PostMapping
    public ApiResponse<PaymentV1Dto.PayResponse> pay(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw,
        @RequestBody PaymentV1Dto.PayRequest request
    ) {
        PaymentInfo info = paymentFacade.pay(
            loginId, loginPw, request.orderId(), request.cardType(), request.cardNo());
        return ApiResponse.success(PaymentV1Dto.PayResponse.from(info));
    }

    /**
     * pg-simulator 콜백 수신(03 §3.4). 비동기 결제 결과를 받아 결제·주문을 최종 확정한다.
     * 인증 헤더 없는 외부 통지이며, transactionKey로 우리 결제 레코드를 찾아 처리한다(멱등).
     */
    @PostMapping("/callback")
    public ApiResponse<PaymentV1Dto.PayResponse> callback(
        @RequestBody PaymentV1Dto.CallbackRequest request
    ) {
        PaymentInfo info = paymentFacade.handleCallback(
            request.transactionKey(), request.status(), request.reason());
        return ApiResponse.success(PaymentV1Dto.PayResponse.from(info));
    }
}
