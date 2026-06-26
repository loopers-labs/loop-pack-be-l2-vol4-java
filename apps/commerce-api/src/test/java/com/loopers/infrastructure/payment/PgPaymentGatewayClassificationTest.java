package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentRequestException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PgPaymentGateway#classifyRequestFailure} — 저수준 PG 실패 → in-doubt 여부 분류 검증.
 *
 * <p>이 분류가 곧 결제의 PENDING(in-doubt) / FAILED(not-in-doubt) 운명을 가른다. "청구가 일어날 수 없음이
 * 확실"한 것만 not-in-doubt 로 보내고, 조금이라도 청구 가능성이 있으면(타임아웃·5xx) 보수적으로 in-doubt 로 둔다.</p>
 */
@DisplayName("PG 요청 실패 분류 — in-doubt 여부")
class PgPaymentGatewayClassificationTest {

    @DisplayName("4xx 거절: 요청이 닿았으나 청구 안 됨 → not-in-doubt")
    @Test
    void clientError_isNotInDoubt() {
        PaymentRequestException ex = PgPaymentGateway.classifyRequestFailure(
                new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        assertThat(ex.isInDoubt()).isFalse();
        assertThat(ex.getMessage()).contains("거절").contains("400");
    }

    @DisplayName("CircuitBreaker OPEN: 호출 자체를 안 함 → not-in-doubt")
    @Test
    void circuitOpen_isNotInDoubt() {
        CircuitBreaker cb = CircuitBreaker.ofDefaults("pg-test");
        PaymentRequestException ex = PgPaymentGateway.classifyRequestFailure(
                CallNotPermittedException.createCallNotPermittedException(cb));

        assertThat(ex.isInDoubt()).isFalse();
        assertThat(ex.getMessage()).contains("장애");
    }

    @DisplayName("연결 거부(ConnectException): 요청이 PG 에 닿지 못함 → not-in-doubt")
    @Test
    void connectionRefused_isNotInDoubt() {
        PaymentRequestException ex = PgPaymentGateway.classifyRequestFailure(
                new ResourceAccessException("I/O error", new ConnectException("Connection refused")));

        assertThat(ex.isInDoubt()).isFalse();
        assertThat(ex.getMessage()).contains("연결");
    }

    @DisplayName("타임아웃(SocketTimeoutException): 응답만 못 받음, 청구됐을 수 있음 → in-doubt")
    @Test
    void socketTimeout_isInDoubt() {
        PaymentRequestException ex = PgPaymentGateway.classifyRequestFailure(
                new ResourceAccessException("timeout", new SocketTimeoutException("Read timed out")));

        assertThat(ex.isInDoubt()).isTrue();
    }

    @DisplayName("5xx: 부수효과 가능성 있어 청구 여부 불명 → in-doubt")
    @Test
    void serverError_isInDoubt() {
        PaymentRequestException ex = PgPaymentGateway.classifyRequestFailure(
                new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThat(ex.isInDoubt()).isTrue();
    }

    @DisplayName("예상 못 한 예외: 보수적으로 in-doubt")
    @Test
    void unexpected_isInDoubt() {
        PaymentRequestException ex = PgPaymentGateway.classifyRequestFailure(
                new IllegalStateException("PG 응답 본문(data)이 비어 있습니다."));

        assertThat(ex.isInDoubt()).isTrue();
    }
}
