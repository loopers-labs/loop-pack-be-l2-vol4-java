package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PgConnectFailedException;
import com.loopers.domain.payment.PgPermanentException;
import com.loopers.domain.payment.PgProvider;
import com.loopers.domain.payment.PgRequest;
import com.loopers.domain.payment.PgResponse;
import com.loopers.domain.payment.PgStatus;
import com.loopers.domain.payment.PgUnknownException;
import com.loopers.interfaces.api.ApiResponse;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.ConnectException;
import java.util.Objects;

/**
 * PG-Simulator 어댑터. 우리 PaymentGateway Port 를 구현해 외부 PG-Simulator 와 연결한다.
 *
 * 핵심 책임:
 *  1. 우리 PgRequest ↔ PG 직렬화 DTO 변환
 *  2. 외부 응답을 우리 도메인 약속 (PgResponse / PgPermanentException / PgUnknownException) 으로 분류
 *  3. Resilience4j 적용 — Retry / CircuitBreaker / Bulkhead
 *
 * 예외 분류 규칙:
 *  - 정상 응답              → PgResponse
 *  - HTTP 4xx (영구 에러)    → PgPermanentException → 즉시 FAILED
 *  - HTTP 5xx (서버 에러)    → PgUnknownException   → UNKNOWN (폴링 위임)
 *  - Read timeout            → PgUnknownException   → UNKNOWN
 *  - Connect timeout         → PgConnectFailedException → Retry 트리거 (요청 도달 전 실패라 안전)
 */
@Slf4j
@Component
public class PgSimulatorAdapter implements PaymentGateway {

    private static final String CB_REQUEST = "pgPaymentRequest";
    private static final String CB_STATUS = "pgPaymentStatus";

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String callbackUrl;

    public PgSimulatorAdapter(
        @Qualifier("pgRestTemplate") RestTemplate restTemplate,
        @Value("${pg.simulator.base-url}") String baseUrl,
        @Value("${payment.callback-url}") String callbackUrl
    ) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.callbackUrl = callbackUrl;
    }

    @Override
    public PgProvider provider() {
        return PgProvider.PG_SIMULATOR;
    }

    @Override
    public String cbName() {
        return CB_REQUEST;
    }

    @Override
    @CircuitBreaker(name = CB_REQUEST)
    @Retry(name = CB_REQUEST)
    @Bulkhead(name = CB_REQUEST)
    public PgResponse request(PgRequest request) {
        HttpHeaders headers = jsonHeaders(request.userId());
        PgSimulatorDto.Request body = new PgSimulatorDto.Request(
            paddedOrderId(request.orderId()),
            request.cardType().name(),
            request.cardNo(),
            request.amount().amount().longValueExact(),
            callbackUrl
        );

        try {
            ResponseEntity<ApiResponse<PgSimulatorDto.TransactionData>> response = restTemplate.exchange(
                baseUrl + "/api/v1/payments",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<>() {
                }
            );
            return toPgResponse(response);
        } catch (HttpClientErrorException e) {
            throw new PgPermanentException("PG 영구 에러 (4xx): " + e.getStatusCode() + " " + e.getStatusText(), e);
        } catch (HttpServerErrorException e) {
            throw new PgUnknownException("PG 서버 에러 (5xx), 결과 미확정: " + e.getStatusCode(), e);
        } catch (ResourceAccessException e) {
            throw classifyIoFailure(e);
        }
    }

    @Override
    @CircuitBreaker(name = CB_STATUS)
    @Retry(name = CB_STATUS)
    public PgResponse getStatus(String transactionKey, Long userId) {
        HttpHeaders headers = jsonHeaders(userId);

        try {
            ResponseEntity<ApiResponse<PgSimulatorDto.TransactionData>> response = restTemplate.exchange(
                baseUrl + "/api/v1/payments/" + transactionKey,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {
                }
            );
            return toPgResponse(response);
        } catch (HttpClientErrorException.NotFound e) {
            // 폴링 호출인데 transactionKey 가 없다 = PG 에 거래 자체가 없음 → 영구 에러로 본다.
            throw new PgPermanentException("PG 에 거래가 존재하지 않습니다: " + transactionKey, e);
        } catch (HttpClientErrorException e) {
            throw new PgPermanentException("PG 영구 에러 (4xx): " + e.getStatusCode(), e);
        } catch (HttpServerErrorException e) {
            throw new PgUnknownException("PG 서버 에러 (5xx), 상태 미확정: " + e.getStatusCode(), e);
        } catch (ResourceAccessException e) {
            throw classifyIoFailure(e);
        }
    }

    /**
     * I/O 예외를 분류한다. ConnectException 은 retry 안전(요청 도달 전), 나머지는 결과 불확정으로 본다.
     */
    private RuntimeException classifyIoFailure(ResourceAccessException e) {
        if (e.getCause() instanceof ConnectException) {
            return new PgConnectFailedException("PG 연결 실패 — 요청 도달 전 실패로 간주", e);
        }
        return new PgUnknownException("PG 통신 실패 (timeout 등), 결과 미확정: " + e.getMessage(), e);
    }

    private PgResponse toPgResponse(ResponseEntity<ApiResponse<PgSimulatorDto.TransactionData>> response) {
        ApiResponse<PgSimulatorDto.TransactionData> body = response.getBody();
        if (body == null || body.data() == null) {
            throw new PgUnknownException("PG 응답 body 가 비었습니다.");
        }
        PgSimulatorDto.TransactionData data = body.data();
        try {
            PgStatus status = PgStatus.valueOf(data.status());
            return new PgResponse(data.transactionKey(), status, data.reason());
        } catch (IllegalArgumentException e) {
            throw new PgUnknownException("알 수 없는 PG status: " + data.status(), e);
        }
    }

    /** PG-Simulator 는 X-USER-ID 헤더를 요구한다. */
    private HttpHeaders jsonHeaders(Long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-USER-ID", Objects.requireNonNull(userId).toString());
        return headers;
    }

    /** PG validate 가 6자 이상 String 을 요구하므로 zero-padding. 같은 변환을 getStatus 시점에는 transactionKey 로 갈음. */
    private String paddedOrderId(Long orderId) {
        return String.format("%06d", orderId);
    }
}
