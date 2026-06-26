package com.loopers.infrastructure.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
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
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

@SpringBootTest
class PaymentGatewayResilienceIntegrationTest {

    private static final String CIRCUIT_BREAKER_NAME = "pg-simulator";
    private static final int MINIMUM_CALLS_TO_OPEN = 20;

    @Autowired
    private PaymentGateway paymentGateway;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @MockitoBean
    private PgSimulatorClient pgSimulatorClient;

    @BeforeEach
    void resetCircuit() {
        circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER_NAME).reset();
    }

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

    @DisplayName("외부 결제 시스템이 4xx로 응답하면 fallback이 확정 실패(REJECTED)로 처리한다.")
    @Test
    void treatsClientError_asRejected() {
        // arrange
        FeignException clientError = FeignException.errorStatus(
            "requestPayment",
            feign.Response.builder()
                .status(400)
                .reason("bad request")
                .request(request())
                .headers(Collections.emptyMap())
                .build()
        );
        given(pgSimulatorClient.requestPayment(any(), any())).willThrow(clientError);

        // act
        PaymentRequestResult result = paymentGateway.requestPayment(payment());

        // assert
        assertThat(result.outcome()).isEqualTo(PaymentRequestResult.Outcome.REJECTED);
    }

    @DisplayName("유저 결제 요청은 타임아웃이어도 재시도하지 않고 PG를 정확히 한 번만 호출한 뒤 결과 불명으로 빠르게 응답한다.")
    @Test
    void userPath_failsFast_withoutRetry() {
        // arrange
        RetryableException timeout = new RetryableException(
            -1, "read timed out", Request.HttpMethod.POST,
            new SocketTimeoutException("Read timed out"), (Long) null, request());
        given(pgSimulatorClient.requestPayment(any(), any())).willThrow(timeout);

        // act
        PaymentRequestResult result = paymentGateway.requestPayment(payment());

        // assert
        assertAll(
            () -> assertThat(result.outcome()).isEqualTo(PaymentRequestResult.Outcome.UNKNOWN),
            () -> verify(pgSimulatorClient, times(1)).requestPayment(any(), any())
        );
    }

    @DisplayName("타임아웃이 임계치를 넘으면 서킷이 열리고, 이후 호출은 PG를 부르지 않고 결과 불명으로 흡수한다.")
    @Test
    void opensCircuitOnRepeatedTimeout_thenShortCircuits() {
        // arrange
        RetryableException timeout = new RetryableException(
            -1, "read timed out", Request.HttpMethod.POST,
            new SocketTimeoutException("Read timed out"), (Long) null, request());
        given(pgSimulatorClient.requestPayment(any(), any())).willThrow(timeout);
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER_NAME);

        // act
        for (int i = 0; i < MINIMUM_CALLS_TO_OPEN; i++) {
            paymentGateway.requestPayment(payment());
        }
        clearInvocations(pgSimulatorClient);
        PaymentRequestResult shortCircuited = paymentGateway.requestPayment(payment());

        // assert
        assertAll(
            () -> assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN),
            () -> assertThat(shortCircuited.outcome()).isEqualTo(PaymentRequestResult.Outcome.UNKNOWN),
            () -> verify(pgSimulatorClient, never()).requestPayment(any(), any())
        );
    }

    @DisplayName("서킷이 열린 상태에서는 PG를 호출하지 않고 즉시 결과 불명(UNKNOWN)으로 흡수한다.")
    @Test
    void whenOpen_skipsGatewayCall() {
        // arrange
        circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER_NAME).transitionToOpenState();

        // act
        PaymentRequestResult result = paymentGateway.requestPayment(payment());

        // assert
        assertAll(
            () -> assertThat(result.outcome()).isEqualTo(PaymentRequestResult.Outcome.UNKNOWN),
            () -> verify(pgSimulatorClient, never()).requestPayment(any(), any())
        );
    }
}
