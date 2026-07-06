package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentApplicationService;
import com.loopers.application.payment.PaymentRequestInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.AuthUser;
import com.loopers.interfaces.api.user.AuthUserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 결제 API.
 *
 * <ul>
 *   <li>{@code POST /} — pg-simulator 비동기 결제 요청 → transactionKey(PENDING) 반환</li>
 *   <li>{@code POST /callback} — pg-simulator 비동기 콜백 수신 (인증 불필요)</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentV1Controller {

    private final PaymentApplicationService paymentApplicationService;

    @PostMapping
    public ApiResponse<PaymentV1Dto.PaymentResponse> requestPayment(
        @AuthUser AuthUserContext authUser,
        @Valid @RequestBody PaymentV1Dto.PaymentRequest request
    ) {
        PaymentRequestInfo info = paymentApplicationService.requestPayment(
            authUser.userId(), request.orderId(), request.cardType(), request.cardNo(), request.amount());
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.pending(info.transactionKey()));
    }

    /**
     * pg-simulator 콜백 엔드포인트.
     *
     * <p>callbackUrl = {@code http://localhost:8080/api/v1/payments/callback} 로 등록되며,
     * pg-simulator 가 결제 처리 완료 후 이 엔드포인트를 호출한다.
     * 인증 헤더 없이 수신하므로 {@code @AuthUser} 를 사용하지 않는다.
     */
    @PostMapping("/callback")
    public ApiResponse<Void> handleCallback(
        @Valid @RequestBody PaymentV1Dto.CallbackRequest request
    ) {
        log.info("[Callback] 수신 — transactionKey={}, orderId={}, status={}", request.transactionKey(), request.orderId(), request.status());
        paymentApplicationService.handleCallback(
            request.transactionKey(),
            Long.parseLong(request.orderId()),
            request.status(),
            request.reason()
        );
        return ApiResponse.success(null);
    }
}
