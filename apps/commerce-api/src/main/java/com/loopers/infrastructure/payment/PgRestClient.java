package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PgClient;
import com.loopers.domain.payment.PgPaymentRequest;
import com.loopers.domain.payment.PgTransactionResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

@Slf4j
@Component
public class PgRestClient implements PgClient {

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
    public PgTransactionResponse requestPayment(PgPaymentRequest request) {
        String url = pgBaseUrl + "/api/v1/payments";
        return Optional.ofNullable(pgRequestRestTemplate.postForObject(url, request, PgTransactionResponse.class))
            .orElseThrow(() -> new CoreException(ErrorType.PAYMENT_GATEWAY_ERROR, "PG 응답이 비어있습니다."));
    }

    @CircuitBreaker(name = "pgClient", fallbackMethod = "getTransactionFallback")
    @Override
    public PgTransactionResponse getTransaction(String transactionKey) {
        String url = pgBaseUrl + "/api/v1/payments/" + transactionKey;
        return Optional.ofNullable(pgQueryRestTemplate.getForObject(url, PgTransactionResponse.class))
            .orElseThrow(() -> new CoreException(ErrorType.PG_QUERY_ERROR, "PG 조회 응답이 비어있습니다."));
    }

    private PgTransactionResponse requestPaymentFallback(PgPaymentRequest request, Throwable t) {
        log.error("PG 결제 요청 실패. 서킷브레이커 fallback 실행. cause: {}", t.getMessage());
        throw new CoreException(ErrorType.PAYMENT_GATEWAY_ERROR, "PG 결제 요청에 실패했습니다.");
    }

    private PgTransactionResponse getTransactionFallback(String transactionKey, Throwable t) {
        log.error("PG 결제 조회 실패. transactionKey: {}, cause: {}", transactionKey, t.getMessage());
        throw new CoreException(ErrorType.PG_QUERY_ERROR, "PG 결제 조회에 실패했습니다.");
    }
}
