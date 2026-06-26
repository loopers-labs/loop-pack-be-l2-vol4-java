package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PgClientException;
import com.loopers.domain.payment.PgPaymentCommand;
import com.loopers.domain.payment.PgPaymentResult;
import com.loopers.domain.payment.PgTransactionStatus;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("infra")
class ResilientPgClientTest {

    private final PgClientImpl delegate = mock(PgClientImpl.class);
    private final ResilientPgClient resilientPgClient = new ResilientPgClient(delegate);

    private static PgPaymentCommand command() {
        return new PgPaymentCommand("1", "001234", CardType.SAMSUNG, "1234-5678-9814-1451", 5000L,
            "http://localhost:8080/api/v1/payments/callback");
    }

    @Test
    @DisplayName("정상 시 delegate 결과를 그대로 반환한다")
    void delegatesOnSuccess() {
        // arrange
        PgPaymentResult expected = new PgPaymentResult("TR:abc", PgTransactionStatus.PENDING, null);
        when(delegate.requestPayment(command())).thenReturn(expected);

        // act
        PgPaymentResult result = resilientPgClient.requestPayment(command());

        // assert
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("폴백은 회로 차단/장애를 PgClientException 으로 변환해, 호출자가 정상 응답할 수 있게 한다")
    void fallbackTranslatesToPgClientException() {
        // arrange: 회로 OPEN 시 resilience4j 가 던지는 예외
        CallNotPermittedException circuitOpen = mock(CallNotPermittedException.class);

        // act & assert
        assertThatThrownBy(() -> resilientPgClient.requestPaymentFallback(command(), circuitOpen))
            .isInstanceOf(PgClientException.class);
    }
}
