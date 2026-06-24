package com.loopers.infrastructure.pg;

import com.loopers.config.PgProperties;
import com.loopers.domain.pg.PgGateway;
import com.loopers.domain.pg.PgTransactionResult;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class PgSimulatorGateway implements PgGateway {

    private final RestTemplate pgRestTemplate;
    private final PgProperties pgProperties;

    @CircuitBreaker(name = "pgCircuit", fallbackMethod = "fallback")
    @Retry(name = "pgRetry")
    @Override
    @SuppressWarnings("unchecked")
    public PgTransactionResult request(String userId, String orderId, String cardType, String cardNo, Long amount, String callbackUrl) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-USER-ID", userId);

        Map<String, Object> body = Map.of(
            "orderId", orderId,
            "cardType", cardType,
            "cardNo", cardNo,
            "amount", amount,
            "callbackUrl", callbackUrl
        );

        try {
            Map<String, Object> response = pgRestTemplate.postForObject(
                pgProperties.getSimulatorUrl() + "/api/v1/payments",
                new HttpEntity<>(body, headers),
                Map.class
            );
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            return new PgTransactionResult(
                (String) data.get("transactionKey"),
                (String) data.get("status"),
                (String) data.get("reason")
            );
        } catch (HttpClientErrorException e) {
            // 4xx — 재시도/CB 대상 아님, 즉시 실패
            throw new CoreException(ErrorType.INTERNAL_ERROR, "PG 결제 요청 실패: " + e.getResponseBodyAsString());
        }
        // 5xx(HttpServerErrorException), 네트워크 오류(ResourceAccessException) → Retry → CB → fallback
    }

    public PgTransactionResult fallback(String userId, String orderId, String cardType, String cardNo, Long amount, String callbackUrl, Throwable t) {
        throw new CoreException(ErrorType.INTERNAL_ERROR, "잠시 후 다시 시도해주세요");
    }
}
