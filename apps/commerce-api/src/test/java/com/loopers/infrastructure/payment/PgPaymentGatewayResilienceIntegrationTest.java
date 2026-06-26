package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PG 어댑터의 Retry 정책을 실제 RestClient + 인터셉터로 못박는 통합 테스트.
 *
 * <p>인터셉터가 진짜 500 응답/{@code SocketTimeoutException}/{@code ConnectException} 을 만들어내고,
 * RestClient 가 이를 실제 예외로 래핑 → 실제 {@code @Retry}/{@code @CircuitBreaker} aspect 가 감싼다.
 * 인터셉터 호출 횟수 = 실제 HTTP 시도 횟수이므로, 재시도 횟수를 직접 검증할 수 있다.</p>
 *
 * <p>각 테스트 전 CircuitBreaker("pg") 를 reset 해 이전 테스트의 실패 집계가 누적돼 CB 가 열리는 오염을 막는다.</p>
 */
@SpringBootTest
class PgPaymentGatewayResilienceIntegrationTest {

    @Autowired
    private PaymentGateway paymentGateway;

    @Autowired
    private StubPgInterceptor stub;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void reset() {
        circuitBreakerRegistry.circuitBreaker("pg").reset();
        stub.reset();
    }

    private static PaymentGateway.Command command() {
        return new PaymentGateway.Command(
                "1", "100", CardType.SAMSUNG, "1234-5678-9814-1451", 5_000L, "http://localhost/callback");
    }

    @DisplayName("재시도하는 실패(빠른 실패)는 최초 1 + 재시도 2 = 총 3회 시도 후 503 으로 떨어진다. ")
    @Nested
    class RetriedFailures {

        @DisplayName("5xx 는 3회까지 시도한다. (이 PG 에선 깨끗한 롤백 → 재시도 안전)")
        @Test
        void serverError5xx_retriesUpToThreeAttempts() {
            stub.mode(StubPgInterceptor.Mode.SERVER_ERROR);

            assertThatThrownBy(() -> paymentGateway.request(command()))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.SERVICE_UNAVAILABLE));

            assertThat(stub.count()).isEqualTo(3);
        }

        @DisplayName("연결 거부(ConnectException)도 3회까지 시도한다. (요청이 PG 에 닿지 못함 → not in-doubt)")
        @Test
        void connectRefused_retriesUpToThreeAttempts() {
            stub.mode(StubPgInterceptor.Mode.CONNECT_REFUSED);

            assertThatThrownBy(() -> paymentGateway.request(command()))
                    .isInstanceOf(CoreException.class);

            assertThat(stub.count()).isEqualTo(3);
        }

        @DisplayName("find(조회)도 동일한 Retry 설정을 공유해 5xx 를 3회 시도한다.")
        @Test
        void find_serverError5xx_retriesUpToThreeAttempts() {
            stub.mode(StubPgInterceptor.Mode.SERVER_ERROR);

            assertThatThrownBy(() -> paymentGateway.find("20250816:TR:9577c5", "1"))
                    .isInstanceOf(CoreException.class);

            assertThat(stub.count()).isEqualTo(3);
        }
    }

    @DisplayName("재시도하지 않는 실패는 단 1회만 시도하고 즉시 503 으로 떨어진다. ")
    @Nested
    class NonRetriedFailures {

        @DisplayName("read 타임아웃(SocketTimeoutException)은 재시도하지 않는다. (in-doubt → 중복 위험)")
        @Test
        void readTimeout_singleAttemptNoRetry() {
            stub.mode(StubPgInterceptor.Mode.READ_TIMEOUT);

            assertThatThrownBy(() -> paymentGateway.request(command()))
                    .isInstanceOf(CoreException.class);

            assertThat(stub.count()).isEqualTo(1);
        }

        @DisplayName("4xx(결정론적 실패)는 재시도하지 않고, in-doubt 와 구분되도록 BAD_REQUEST 로 표면화한다.")
        @Test
        void clientError4xx_singleAttemptNoRetry() {
            stub.mode(StubPgInterceptor.Mode.CLIENT_ERROR);

            assertThatThrownBy(() -> paymentGateway.request(command()))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));

            assertThat(stub.count()).isEqualTo(1);
        }
    }

    @DisplayName("PG 계약(orderId ≥ 6자)에 맞춰 짧은 주문 PK 는 0 패딩으로 전송한다.")
    @Test
    void shortOrderId_isZeroPaddedToSixChars() {
        stub.mode(StubPgInterceptor.Mode.CLIENT_ERROR); // 응답은 무관 — 전송된 바디만 검증

        PaymentGateway.Command shortOrder = new PaymentGateway.Command(
                "1", "100", CardType.SAMSUNG, "1234-5678-9814-1451", 5_000L, "http://localhost/callback");
        assertThatThrownBy(() -> paymentGateway.request(shortOrder)).isInstanceOf(CoreException.class);

        assertThat(stub.lastBody()).contains("\"orderId\":\"000100\"");
    }

    @TestConfiguration
    static class StubConfig {

        @Bean
        StubPgInterceptor stubPgInterceptor() {
            return new StubPgInterceptor();
        }

        /** 실제 PG 빈을 대체하는 stub RestClient. 인터셉터가 소켓 연결 전에 응답을 단락(short-circuit)한다. */
        @Bean
        @Primary
        RestClient stubPgRestClient(StubPgInterceptor interceptor) {
            return RestClient.builder()
                    .baseUrl("http://localhost:8082")
                    .requestInterceptor(interceptor)
                    .build();
        }
    }

    /** 모드에 따라 실제 HTTP 실패를 생성하고, 호출 횟수(=시도 횟수)를 센다. */
    static class StubPgInterceptor implements ClientHttpRequestInterceptor {

        enum Mode { SERVER_ERROR, CLIENT_ERROR, READ_TIMEOUT, CONNECT_REFUSED }

        private volatile Mode mode = Mode.SERVER_ERROR;
        private final AtomicInteger count = new AtomicInteger();
        private volatile String lastBody = "";

        void mode(Mode mode) {
            this.mode = mode;
        }

        void reset() {
            count.set(0);
            lastBody = "";
        }

        int count() {
            return count.get();
        }

        String lastBody() {
            return lastBody;
        }

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
                throws IOException {
            count.incrementAndGet();
            lastBody = new String(body, java.nio.charset.StandardCharsets.UTF_8);
            return switch (mode) {
                case SERVER_ERROR -> new MockClientHttpResponse(new byte[0], HttpStatus.INTERNAL_SERVER_ERROR);
                case CLIENT_ERROR -> new MockClientHttpResponse(new byte[0], HttpStatus.BAD_REQUEST);
                case READ_TIMEOUT -> throw new SocketTimeoutException("Read timed out");
                case CONNECT_REFUSED -> throw new ConnectException("Connection refused");
            };
        }
    }
}
