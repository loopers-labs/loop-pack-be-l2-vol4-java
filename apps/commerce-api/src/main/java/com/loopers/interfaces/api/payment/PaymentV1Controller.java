package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.application.payment.ReconcileOutcome;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.AdminAuth;
import com.loopers.interfaces.api.user.AuthHeaders;
import jakarta.validation.Valid;
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

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentV1Controller implements PaymentV1ApiSpec {

    private final PaymentFacade paymentFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Override
    public ApiResponse<PaymentV1Dto.PaymentResponse> pay(
            @RequestHeader(AuthHeaders.LOGIN_ID) String loginId,
            @RequestHeader(AuthHeaders.LOGIN_PW) String loginPw,
            @Valid @RequestBody PaymentV1Dto.PaymentRequest request
    ) {
        PaymentInfo info = paymentFacade.pay(loginId, loginPw, request.orderId(), request.cardType(), request.cardNo());
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(info));
    }

    @GetMapping("/{paymentId}")
    @Override
    public ApiResponse<PaymentV1Dto.PaymentStatusResponse> get(
            @RequestHeader(AuthHeaders.LOGIN_ID) String loginId,
            @RequestHeader(AuthHeaders.LOGIN_PW) String loginPw,
            @PathVariable Long paymentId
    ) {
        PaymentInfo info = paymentFacade.getPayment(loginId, loginPw, paymentId);
        return ApiResponse.success(PaymentV1Dto.PaymentStatusResponse.from(info));
    }

    /**
     * PG 콜백 수신(공개 엔드포인트). commerce-api 는 전역 인증 인터셉터가 없고 각 핸들러가
     * @RequestHeader 로 로그인 정보를 받으므로, 로그인 헤더를 선언하지 않은 이 메서드는 자연히 공개된다.
     * 진위 검증은 생략하고(설계 §10-3), 무결성 가드(amount·cardNo 대조)가 1차 방어선이 된다.
     */
    @PostMapping("/callback")
    @Override
    public ApiResponse<Void> callback(@RequestBody PaymentV1Dto.CallbackRequest request) {
        paymentFacade.confirmResult(
                request.transactionKey(), request.status(), request.reason(), request.amount(), request.cardNo());
        return ApiResponse.success(null);
    }

    @PostMapping("/{paymentId}/reconcile")
    @Override
    public ApiResponse<PaymentV1Dto.ReconcileResponse> reconcile(
            @RequestHeader(AuthHeaders.LDAP) String ldap,
            @PathVariable Long paymentId
    ) {
        AdminAuth.validate(ldap);
        ReconcileOutcome outcome = paymentFacade.reconcileManually(paymentId);
        return ApiResponse.success(new PaymentV1Dto.ReconcileResponse(paymentId, outcome.name()));
    }
}
