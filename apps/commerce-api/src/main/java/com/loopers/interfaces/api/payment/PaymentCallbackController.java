package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PG → commerce 콜백 수신.
 * pg-simulator 는 callbackUrl(http://localhost:8080/api/v1/payments/callback) 로 결과를 POST 한다.
 * 콜백은 best-effort(유실 가능)이므로, 못 받아도 폴링 복구가 결과를 맞춘다.
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentCallbackController {

    private final PaymentFacade paymentFacade;

    @PostMapping("/callback")
    public ApiResponse<Object> handleCallback(@RequestBody PaymentV1Dto.CallbackRequest request) {
        log.info("[콜백] txKey={}, status={}, reason={}",
                request.transactionKey(), request.status(), request.reason());
        paymentFacade.handleCallback(request.transactionKey(), request.toPgStatus(), request.reason());
        return ApiResponse.success();
    }
}
