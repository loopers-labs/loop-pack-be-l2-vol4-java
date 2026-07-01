package com.loopers.infrastructure.pg;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class RestTemplatePgPaymentClient implements PgPaymentClient {

    private static final String HEADER_USER_ID = "X-USER-ID";
    private static final ParameterizedTypeReference<PgApiResponse<PgTransactionResponse>> TRANSACTION_TYPE =
        new ParameterizedTypeReference<>() {};

    private final RestTemplate restTemplate;

    @Value("${pg.base-url}")
    private String baseUrl;

    public RestTemplatePgPaymentClient(@Qualifier("pgRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public PgPaymentResult request(PgPaymentRequest request) {
        HttpHeaders headers = buildHeaders(request.userId());

        Map<String, Object> body = Map.of(
            "orderId", String.format("%010d", request.orderId()),
            "cardType", request.cardType(),
            "cardNo", request.cardNo(),
            "amount", request.amount(),
            "callbackUrl", request.callbackUrl()
        );

        try {
            var response = restTemplate.exchange(
                baseUrl + "/api/v1/payments",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                TRANSACTION_TYPE
            );

            PgApiResponse<PgTransactionResponse> apiResponse = response.getBody();
            if (apiResponse == null || !apiResponse.isSuccess() || apiResponse.data() == null) {
                throw new CoreException(ErrorType.BAD_REQUEST, "PG 결제 요청에 실패했습니다.");
            }

            log.info("PG 결제 요청 성공 — transactionKey={}", apiResponse.data().transactionKey());
            return new PgPaymentResult(apiResponse.data().transactionKey());

        } catch (CoreException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("PG 결제 요청 실패 — orderId={}, reason={}", request.orderId(), e.getMessage());
            throw new CoreException(ErrorType.BAD_REQUEST, "PG 결제 요청 중 오류가 발생했습니다.");
        }
    }

    @Override
    public Optional<PgTransactionResponse> getStatus(String transactionKey, Long userId) {
        HttpHeaders headers = buildHeaders(userId);
        try {
            var response = restTemplate.exchange(
                baseUrl + "/api/v1/payments/{transactionKey}",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                TRANSACTION_TYPE,
                transactionKey
            );
            PgApiResponse<PgTransactionResponse> apiResponse = response.getBody();
            if (apiResponse == null || !apiResponse.isSuccess() || apiResponse.data() == null) {
                return Optional.empty();
            }
            return Optional.of(apiResponse.data());
        } catch (RestClientException e) {
            log.warn("PG 결제 상태 조회 실패 — transactionKey={}, reason={}", transactionKey, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<PgTransactionResponse> findByOrderId(String orderId, Long userId) {
        HttpHeaders headers = buildHeaders(userId);
        try {
            var response = restTemplate.exchange(
                baseUrl + "/api/v1/payments?orderId={orderId}",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                TRANSACTION_TYPE,
                orderId
            );
            PgApiResponse<PgTransactionResponse> apiResponse = response.getBody();
            if (apiResponse == null || !apiResponse.isSuccess() || apiResponse.data() == null) {
                return Optional.empty();
            }
            return Optional.of(apiResponse.data());
        } catch (RestClientException e) {
            log.warn("PG orderId 조회 실패 — orderId={}, reason={}", orderId, e.getMessage());
            return Optional.empty();
        }
    }

    private HttpHeaders buildHeaders(Long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HEADER_USER_ID, String.valueOf(userId));
        return headers;
    }

    // PG ApiResponse 래퍼 역직렬화용 — pg-simulator의 ApiResponse<T> 구조와 대응
    record PgApiResponse<T>(Meta meta, T data) {
        boolean isSuccess() {
            return meta != null && "SUCCESS".equals(meta.result());
        }

        record Meta(String result, String errorCode, String message) {}
    }
}
