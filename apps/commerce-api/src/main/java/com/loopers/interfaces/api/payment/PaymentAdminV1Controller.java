package com.loopers.interfaces.api.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.payment.PaymentFacade;
import com.loopers.interfaces.api.common.response.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.pg.PgHmacVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-webhook/v1/payments")
public class PaymentAdminV1Controller implements PaymentAdminV1ApiSpec {

    private final PaymentFacade paymentFacade;
    private final PgHmacVerifier pgHmacVerifier;
    private final ObjectMapper objectMapper;

    @PostMapping("/confirm")
    @Override
    public ApiResponse<PaymentV1Dto.PaymentResponse> confirm(
        @RequestHeader(name = "X-PG-Signature", required = false) String signature,
        @RequestBody String rawBody
    ) {
        pgHmacVerifier.verify(rawBody, signature);
        PaymentV1Dto.ConfirmRequest request = deserialize(rawBody, PaymentV1Dto.ConfirmRequest.class);
        return ApiResponse.success(
            PaymentV1Dto.PaymentResponse.from(
                paymentFacade.confirm(request.orderId(), request.pgTransactionId(), request.amount())
            )
        );
    }

    @PostMapping("/fail")
    @Override
    public ApiResponse<PaymentV1Dto.PaymentResponse> fail(
        @RequestHeader(name = "X-PG-Signature", required = false) String signature,
        @RequestBody String rawBody
    ) {
        pgHmacVerifier.verify(rawBody, signature);
        PaymentV1Dto.FailRequest request = deserialize(rawBody, PaymentV1Dto.FailRequest.class);
        return ApiResponse.success(
            PaymentV1Dto.PaymentResponse.from(
                paymentFacade.fail(request.orderId(), request.pgTransactionId(), request.amount())
            )
        );
    }

    private <T> T deserialize(String rawBody, Class<T> clazz) {
        try {
            return objectMapper.readValue(rawBody, clazz);
        } catch (Exception e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "요청 바디 파싱 오류: " + e.getMessage());
        }
    }
}
