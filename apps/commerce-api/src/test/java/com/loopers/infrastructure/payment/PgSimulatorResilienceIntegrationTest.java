package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PgClient;
import com.loopers.domain.payment.PgPaymentRequest;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 어댑터 회복성의 <b>AOP 배선</b>을 실제 컨텍스트에서 검증한다. 단위 테스트(PgSimulatorClientTest)는 프록시가 없어
 * @Retry/@CircuitBreaker/fallbackMethod가 동작하지 않으므로, 폴백 메서드 시그니처 매칭과 예외 변환은 여기서 확인한다.
 */
@SpringBootTest
@DisplayName("PgSimulatorClient 회복성(AOP)")
class PgSimulatorResilienceIntegrationTest {

    @Autowired
    PgClient pgClient; // @Retry/@CircuitBreaker로 감싸진 AOP 프록시 빈

    @MockitoBean
    PgSimulatorFeignClient feignClient;

    @Test
    @DisplayName("PG 호출이 끝내 실패하면 @Retry 폴백이 인프라 예외를 CoreException(503)으로 변환한다")
    void given_pgFailure_when_requestPayment_then_fallbackThrowsServiceUnavailable() {
        when(feignClient.requestPayment(any(), any()))
                .thenThrow(new RuntimeException("PG down"));

        Throwable thrown = catchThrowable(() -> pgClient.requestPayment(new PgPaymentRequest(
                1234567890123L, 1L, CardType.SAMSUNG, "1234-5678-9814-1451", 5000L,
                "http://localhost:8080/api/v1/payments/callback")));

        assertThat(thrown).isInstanceOf(CoreException.class);
        assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.SERVICE_UNAVAILABLE);
    }
}
