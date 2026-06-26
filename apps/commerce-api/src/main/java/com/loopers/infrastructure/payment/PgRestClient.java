package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PgClient;
import com.loopers.domain.payment.PgPaymentRequest;
import com.loopers.domain.payment.PgTransactionResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

@Slf4j
@Component
public class PgRestClient implements PgClient {

    private static final String USER_ID_HEADER = "X-USER-ID";

    private final RestTemplate pgRequestRestTemplate;
    private final RestTemplate pgQueryRestTemplate;
    private final String pgBaseUrl;

    public PgRestClient(
        @Qualifier("pgRequestRestTemplate") RestTemplate pgRequestRestTemplate,
        @Qualifier("pgQueryRestTemplate") RestTemplate pgQueryRestTemplate,
        @Value("${pg.base-url}") String pgBaseUrl
    ) {
        this.pgRequestRestTemplate = pgRequestRestTemplate;
        this.pgQueryRestTemplate = pgQueryRestTemplate;
        this.pgBaseUrl = pgBaseUrl;
    }

    @CircuitBreaker(name = "pgClient", fallbackMethod = "requestPaymentFallback")
    @Override
    public PgTransactionResponse requestPayment(PgPaymentRequest request, String userId) {
        String url = pgBaseUrl + "/api/v1/payments";
        HttpEntity<PgPaymentRequest> entity = new HttpEntity<>(request, userIdHeaders(userId));
        ResponseEntity<PgApiResponse<PgTransactionResponse>> response = pgRequestRestTemplate.exchange(
            url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
        return Optional.ofNullable(response.getBody())
            .map(PgApiResponse::data)
            .orElseThrow(() -> new CoreException(ErrorType.PAYMENT_GATEWAY_ERROR, "PG 응답이 비어있습니다."));
    }

    @CircuitBreaker(name = "pgClient", fallbackMethod = "getTransactionFallback")
    @Override
    public PgTransactionResponse getTransaction(String transactionKey, String userId) {
        String url = pgBaseUrl + "/api/v1/payments/" + transactionKey;
        HttpEntity<Void> entity = new HttpEntity<>(userIdHeaders(userId));
        ResponseEntity<PgApiResponse<PgTransactionResponse>> response = pgQueryRestTemplate.exchange(
            url, HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});
        return Optional.ofNullable(response.getBody())
            .map(PgApiResponse::data)
            .orElseThrow(() -> new CoreException(ErrorType.PG_QUERY_ERROR, "PG 조회 응답이 비어있습니다."));
    }

    private HttpHeaders userIdHeaders(String userId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "PG 연동에는 유저 ID(X-USER-ID)가 필수입니다.");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set(USER_ID_HEADER, String.valueOf(userId));
        return headers;
    }

    // resilience4j 는 가장 구체적인 예외 타입의 fallback 을 선택한다.
    // 서킷 OPEN(CallNotPermittedException) → 503, 그 외 PG 호출 실패 → 500 으로 분리 매핑한다.
    PgTransactionResponse requestPaymentFallback(PgPaymentRequest request, String userId, CallNotPermittedException t) {
        log.warn("PG 결제 요청 차단. 서킷브레이커 OPEN. cause: {}", t.getMessage());
        throw new CoreException(ErrorType.CIRCUIT_OPEN, "결제 시스템이 일시적으로 불안정하여 요청을 차단했습니다.");
    }

    PgTransactionResponse requestPaymentFallback(PgPaymentRequest request, String userId, Throwable t) {
        log.error("PG 결제 요청 실패. 서킷브레이커 fallback 실행. cause: {}", t.getMessage());
        throw new CoreException(ErrorType.PAYMENT_GATEWAY_ERROR, "PG 결제 요청에 실패했습니다.");
    }

    PgTransactionResponse getTransactionFallback(String transactionKey, String userId, CallNotPermittedException t) {
        log.warn("PG 결제 조회 차단. 서킷브레이커 OPEN. transactionKey: {}, cause: {}", transactionKey, t.getMessage());
        throw new CoreException(ErrorType.CIRCUIT_OPEN, "결제 시스템이 일시적으로 불안정하여 요청을 차단했습니다.");
    }

    PgTransactionResponse getTransactionFallback(String transactionKey, String userId, Throwable t) {
        log.error("PG 결제 조회 실패. transactionKey: {}, cause: {}", transactionKey, t.getMessage());
        throw new CoreException(ErrorType.PG_QUERY_ERROR, "PG 결제 조회에 실패했습니다.");
    }
}
