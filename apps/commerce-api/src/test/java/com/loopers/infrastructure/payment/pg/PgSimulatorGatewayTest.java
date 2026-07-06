package com.loopers.infrastructure.payment.pg;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PgIndeterminateException;
import com.loopers.domain.payment.PgRequestRejectedException;
import feign.FeignException;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PgSimulatorGateway 재시도/예외 매핑 단위 테스트.
 *
 * <p>CircuitBreaker 프록시는 배제하고 재시도 분기 로직만 검증한다 —
 * {@link PgCircuitClient} 를 목으로 대체해 예외 종류별 호출 횟수와 매핑된 예외를 확인한다.
 * (협력 객체 호출 검증이 핵심이므로 Mockito 사용)
 */
@ExtendWith(MockitoExtension.class)
class PgSimulatorGatewayTest {

    @Mock private PgCircuitClient pgCircuitClient;
    @Mock private PgSimulatorClient pgSimulatorClient;
    @InjectMocks private PgSimulatorGateway gateway;

    @DisplayName("요청이 성공하면 transactionKey 를 반환하고 재시도하지 않는다.")
    @Test
    void returnsTransactionKey_whenRequestSucceeds() {
        // arrange
        when(pgCircuitClient.requestOnce(anyString(), any()))
            .thenReturn(new PgPaymentDto.TransactionResponse("TR:success", "PENDING", null));

        // act
        String tid = gateway.requestPayment("1", 100L, CardType.SAMSUNG, "1234-5678-9814-1451", 5000L, "http://localhost:8080/cb");

        // assert
        assertThat(tid).isEqualTo("TR:success");
        verify(pgCircuitClient, times(1)).requestOnce(anyString(), any());
    }

    @DisplayName("500(요청 거부)이 계속되면 최대 3회 재시도 후 PgRequestRejectedException 을 던진다.")
    @Test
    void retriesThreeTimesThenRejects_whenAlways500() {
        // arrange — 매 호출 500
        when(pgCircuitClient.requestOnce(anyString(), any()))
            .thenThrow(mock500());

        // act & assert
        assertThatThrownBy(() ->
            gateway.requestPayment("1", 100L, CardType.SAMSUNG, "1234-5678-9814-1451", 5000L, "http://localhost:8080/cb"))
            .isInstanceOf(PgRequestRejectedException.class);

        // 각 시도가 개별 호출 — 정확히 3회
        verify(pgCircuitClient, times(3)).requestOnce(anyString(), any());
    }

    @DisplayName("500 후 재시도가 성공하면 transactionKey 를 반환한다.")
    @Test
    void retriesThenSucceeds_when500ThenSuccess() {
        // arrange — 1차 500, 2차 성공
        when(pgCircuitClient.requestOnce(anyString(), any()))
            .thenThrow(mock500())
            .thenReturn(new PgPaymentDto.TransactionResponse("TR:retry-ok", "PENDING", null));

        // act
        String tid = gateway.requestPayment("1", 100L, CardType.SAMSUNG, "1234-5678-9814-1451", 5000L, "http://localhost:8080/cb");

        // assert
        assertThat(tid).isEqualTo("TR:retry-ok");
        verify(pgCircuitClient, times(2)).requestOnce(anyString(), any());
    }

    @DisplayName("타임아웃은 재시도하지 않고 즉시 PgIndeterminateException 을 던진다.")
    @Test
    void throwsIndeterminateWithoutRetry_whenTimeout() {
        // arrange — 타임아웃(RetryableException)
        RetryableException timeout = mock(RetryableException.class);
        when(pgCircuitClient.requestOnce(anyString(), any())).thenThrow(timeout);

        // act & assert
        assertThatThrownBy(() ->
            gateway.requestPayment("1", 100L, CardType.SAMSUNG, "1234-5678-9814-1451", 5000L, "http://localhost:8080/cb"))
            .isInstanceOf(PgIndeterminateException.class);

        // 미확정은 재시도 금지 — 정확히 1회
        verify(pgCircuitClient, times(1)).requestOnce(anyString(), any());
    }

    @DisplayName("서킷 open(CallNotPermittedException)은 요청 미전송 = 미생성 확정이므로 PgRequestRejectedException 을 던진다.")
    @Test
    void throwsRejectedWithoutRetry_whenCircuitOpen() {
        // arrange — 회로 open: CB 가 호출 실행 전 차단 → 요청이 PG 로 나가지 않음
        CallNotPermittedException circuitOpen = mock(CallNotPermittedException.class);
        when(pgCircuitClient.requestOnce(anyString(), any())).thenThrow(circuitOpen);

        // act & assert — 미확정이 아니라 확정 실패 (즉시 실패 + 자원 복구)
        assertThatThrownBy(() ->
            gateway.requestPayment("1", 100L, CardType.SAMSUNG, "1234-5678-9814-1451", 5000L, "http://localhost:8080/cb"))
            .isInstanceOf(PgRequestRejectedException.class);

        verify(pgCircuitClient, times(1)).requestOnce(anyString(), any());
    }

    @DisplayName("200 응답이지만 transactionKey 가 null 이면 재시도 없이 PgIndeterminateException 을 던진다.")
    @Test
    void throwsIndeterminateWithoutRetry_when200ButNullTransactionKey() {
        // arrange — 응답은 왔으나 TID 가 비어 있음
        when(pgCircuitClient.requestOnce(anyString(), any()))
            .thenReturn(new PgPaymentDto.TransactionResponse(null, "PENDING", null));

        // act & assert — 미확정으로 분류, 재시도 금지
        assertThatThrownBy(() ->
            gateway.requestPayment("1", 100L, CardType.SAMSUNG, "1234-5678-9814-1451", 5000L, "http://localhost:8080/cb"))
            .isInstanceOf(PgIndeterminateException.class);

        verify(pgCircuitClient, times(1)).requestOnce(anyString(), any());
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static FeignException.InternalServerError mock500() {
        return mock(FeignException.InternalServerError.class);
    }

    private static <T> T mock(Class<T> type) {
        return org.mockito.Mockito.mock(type);
    }
}
