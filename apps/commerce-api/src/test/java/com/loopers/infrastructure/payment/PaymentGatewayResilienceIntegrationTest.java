package com.loopers.infrastructure.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Collections;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRequestResult;

import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.RetryableException;

@SpringBootTest
class PaymentGatewayResilienceIntegrationTest {

    @Autowired
    private PaymentGateway paymentGateway;

    @MockitoBean
    private PgSimulatorClient pgSimulatorClient;

    private PaymentModel payment() {
        return PaymentModel.builder()
            .orderId(100L)
            .userId(1L)
            .amount(78_000)
            .cardType(CardType.SAMSUNG)
            .rawCardNo("1234-5678-9012-3456")
            .requestedAt(ZonedDateTime.now())
            .build();
    }

    private Request request() {
        return Request.create(
            Request.HttpMethod.POST,
            "http://localhost:8082/api/v1/payments",
            Collections.emptyMap(),
            null,
            StandardCharsets.UTF_8,
            new RequestTemplate()
        );
    }

    @DisplayName("응답 타임아웃이면 fallback이 결과 불명(UNKNOWN)으로 흡수한다.")
    @Test
    void absorbsTimeout_asUnknown() {
        // arrange
        RetryableException timeout = new RetryableException(
            -1, "read timed out", Request.HttpMethod.POST,
            new SocketTimeoutException("Read timed out"), (Long) null, request());
        given(pgSimulatorClient.requestPayment(any(), any())).willThrow(timeout);

        // act
        PaymentRequestResult result = paymentGateway.requestPayment(payment());

        // assert
        assertThat(result.outcome()).isEqualTo(PaymentRequestResult.Outcome.UNKNOWN);
    }

    @DisplayName("외부 결제 시스템이 5xx로 응답하면 fallback이 확정 실패(REJECTED)로 처리한다.")
    @Test
    void treatsServerError_asRejected() {
        // arrange
        FeignException serverError = FeignException.errorStatus(
            "requestPayment",
            feign.Response.builder()
                .status(500)
                .reason("error")
                .request(request())
                .headers(Collections.emptyMap())
                .build()
        );
        given(pgSimulatorClient.requestPayment(any(), any())).willThrow(serverError);

        // act
        PaymentRequestResult result = paymentGateway.requestPayment(payment());

        // assert
        assertThat(result.outcome()).isEqualTo(PaymentRequestResult.Outcome.REJECTED);
    }
}
