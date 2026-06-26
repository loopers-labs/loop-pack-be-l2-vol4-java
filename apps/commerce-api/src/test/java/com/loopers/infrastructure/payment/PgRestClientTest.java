package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PgPaymentRequest;
import com.loopers.domain.payment.PgTransactionResponse;
import com.loopers.domain.payment.PgTransactionStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

@DisplayName("PgRestClient — PG 연동 시 X-USER-ID 헤더를 항상 전송한다.")
class PgRestClientTest {

    private static final String BASE_URL = "http://localhost:8082";
    private static final String USER_ID = "135135";

    private RestTemplate requestRestTemplate;
    private RestTemplate queryRestTemplate;
    private MockRestServiceServer requestServer;
    private MockRestServiceServer queryServer;
    private PgRestClient pgRestClient;

    @BeforeEach
    void setUp() {
        requestRestTemplate = new RestTemplate();
        queryRestTemplate = new RestTemplate();
        requestServer = MockRestServiceServer.createServer(requestRestTemplate);
        queryServer = MockRestServiceServer.createServer(queryRestTemplate);
        pgRestClient = new PgRestClient(requestRestTemplate, queryRestTemplate, BASE_URL);
    }

    @DisplayName("requestPayment 호출 시 X-USER-ID 헤더에 userId가 담겨 전송된다.")
    @Test
    void requestPayment_sendsUserIdHeader() {
        // Arrange
        requestServer.expect(requestTo(BASE_URL + "/api/v1/payments"))
            .andExpect(method(POST))
            .andExpect(header("X-USER-ID", String.valueOf(USER_ID)))
            .andRespond(withSuccess(
                "{\"meta\":{\"result\":\"SUCCESS\",\"errorCode\":null,\"message\":null},"
                    + "\"data\":{\"transactionKey\":\"TX-1\",\"status\":\"PENDING\",\"reason\":null}}",
                MediaType.APPLICATION_JSON));

        var request = new PgPaymentRequest("1", CardType.SAMSUNG, "1234-5678-9814-1451", 10000L,
            "http://localhost:8080/api/v1/payments/callback");

        // Act
        PgTransactionResponse response = pgRestClient.requestPayment(request, USER_ID);

        // Assert
        assertThat(response.transactionKey()).isEqualTo("TX-1");
        assertThat(response.status()).isEqualTo(PgTransactionStatus.PENDING);
        requestServer.verify();
    }

    @DisplayName("requestPayment 는 PG 의 ApiResponse 봉투에서 data 를 풀어 transactionKey/status 를 매핑한다.")
    @Test
    void requestPayment_unwrapsEnvelopeData() {
        // Arrange — 실제 PG 시뮬레이터는 {meta, data} 봉투로 응답한다.
        requestServer.expect(requestTo(BASE_URL + "/api/v1/payments"))
            .andExpect(method(POST))
            .andRespond(withSuccess(
                "{\"meta\":{\"result\":\"SUCCESS\",\"errorCode\":null,\"message\":null},"
                    + "\"data\":{\"transactionKey\":\"20250816:TR:9577c5\",\"status\":\"PENDING\",\"reason\":null}}",
                MediaType.APPLICATION_JSON));

        var request = new PgPaymentRequest("1", CardType.SAMSUNG, "1234-5678-9814-1451", 10000L,
            "http://localhost:8080/api/v1/payments/callback");

        // Act
        PgTransactionResponse response = pgRestClient.requestPayment(request, USER_ID);

        // Assert
        assertThat(response.transactionKey()).isEqualTo("20250816:TR:9577c5");
        assertThat(response.status()).isEqualTo(PgTransactionStatus.PENDING);
        requestServer.verify();
    }

    @DisplayName("서킷브레이커 OPEN(CallNotPermittedException) 으로 차단되면 CIRCUIT_OPEN(503) 으로 매핑한다.")
    @Test
    void requestPaymentFallback_circuitOpen_mapsTo503() {
        // Arrange
        CircuitBreaker breaker = CircuitBreaker.ofDefaults("pgClient");
        breaker.transitionToOpenState();
        CallNotPermittedException notPermitted = CallNotPermittedException.createCallNotPermittedException(breaker);
        var request = new PgPaymentRequest("1", CardType.SAMSUNG, "1234-5678-9814-1451", 10000L,
            "http://localhost:8080/api/v1/payments/callback");

        // Act
        CoreException thrown = catchThrowableOfType(
            () -> pgRestClient.requestPaymentFallback(request, USER_ID, notPermitted), CoreException.class);

        // Assert
        assertThat(thrown.getErrorType()).isEqualTo(ErrorType.CIRCUIT_OPEN);
        assertThat(thrown.getErrorType().getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @DisplayName("PG 요청 실패(그 외 예외) 는 PAYMENT_GATEWAY_ERROR(500) 으로 매핑한다.")
    @Test
    void requestPaymentFallback_otherError_mapsTo500() {
        // Arrange
        var request = new PgPaymentRequest("1", CardType.SAMSUNG, "1234-5678-9814-1451", 10000L,
            "http://localhost:8080/api/v1/payments/callback");

        // Act
        CoreException thrown = catchThrowableOfType(
            () -> pgRestClient.requestPaymentFallback(request, USER_ID, new RuntimeException("boom")), CoreException.class);

        // Assert
        assertThat(thrown.getErrorType()).isEqualTo(ErrorType.PAYMENT_GATEWAY_ERROR);
        assertThat(thrown.getErrorType().getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @DisplayName("getTransaction 호출 시 X-USER-ID 헤더에 userId가 담겨 전송된다.")
    @Test
    void getTransaction_sendsUserIdHeader() {
        // Arrange
        queryServer.expect(requestTo(BASE_URL + "/api/v1/payments/TX-1"))
            .andExpect(method(GET))
            .andExpect(header("X-USER-ID", String.valueOf(USER_ID)))
            .andRespond(withSuccess(
                "{\"meta\":{\"result\":\"SUCCESS\",\"errorCode\":null,\"message\":null},"
                    + "\"data\":{\"transactionKey\":\"TX-1\",\"status\":\"SUCCESS\",\"reason\":null}}",
                MediaType.APPLICATION_JSON));

        // Act
        PgTransactionResponse response = pgRestClient.getTransaction("TX-1", USER_ID);

        // Assert
        assertThat(response.status()).isEqualTo(PgTransactionStatus.SUCCESS);
        queryServer.verify();
    }
}
