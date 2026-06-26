package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.GatewayCommand;
import com.loopers.domain.payment.GatewayResult;
import com.loopers.domain.payment.PaymentGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.ResourceAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 결제 접수(POST) 재시도 가드 — 응답 유실(IO 오류)은 접수됐을 수도 있어 재전송하면 이중 접수 위험.
 * 재시도를 켜도(retry-exceptions=5xx) IO 오류는 재전송하지 않아야 한다. (B-1 가드)
 */
@SpringBootTest
@TestPropertySource(properties = {
    "resilience4j.retry.instances.pgRequestRetry.max-attempts=2",
    "resilience4j.retry.instances.pgRequestRetry.wait-duration=1ms",
    // 5xx만 재시도 — IO 오류(응답 유실)는 재전송 금지
    "resilience4j.retry.instances.pgRequestRetry.retry-exceptions=org.springframework.web.client.HttpServerErrorException"
})
class PgRequestRetryGuardTest {

    @MockitoBean
    private PgClient pgClient;
    @Autowired
    private PaymentGateway paymentGateway;

    private static final GatewayCommand CMD =
        new GatewayCommand(1L, 10L, CardType.SAMSUNG, "1234-5678-9012-3456", 5_000L);

    @DisplayName("응답 유실(IO 오류) 시 결제 요청을 재전송하지 않고 1회만 호출한다 — 이중 접수 방지")
    @Test
    void doesNotRetry_whenResponseLost() {
        // 응답을 못 받은 상황(read-timeout·연결끊김)을 흉내 — 접수가 됐을 수도 있어 재전송하면 이중 거래 위험
        when(pgClient.requestPayment(any())).thenThrow(new ResourceAccessException("Read timed out"));

        GatewayResult result = paymentGateway.requestPayment(CMD); // 재시도 없이 Fallback → pending

        verify(pgClient, times(1)).requestPayment(any());
        assertThat(result.isAccepted()).isFalse();
    }
}
