package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayStatus;
import com.loopers.domain.payment.PaymentMethod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class HttpPaymentGateway implements PaymentGateway {

    private final RestTemplate restTemplate;
    private final io.github.resilience4j.circuitbreaker.CircuitBreaker circuitBreaker;
    private static final String PG_BASE_URL = "http://localhost:8082/api/v1/payments";

    public HttpPaymentGateway(RestTemplate restTemplate, io.github.resilience4j.circuitbreaker.CircuitBreaker circuitBreaker) {
        this.restTemplate = restTemplate;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public PaymentGatewayResult requestPayment(Long orderId, BigDecimal amount, PaymentMethod method) {
        return circuitBreaker.executeSupplier(() -> {
            HttpHeaders headers = createHeaders();
            String formattedOrderId = String.format("%06d", orderId);

            String requestJson = String.format(
                    "{\"orderId\": \"%s\", \"cardType\": \"SAMSUNG\", \"cardNo\": \"1234-1234-1234-1234\", \"amount\": %d, \"callbackUrl\": \"http://localhost:8080/callback\"}",
                    formattedOrderId, amount.longValue()
            );

            HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);

            try {
                log.info("Sending payment request to pg-simulator: {}", requestJson);
                ResponseEntity<PgApiResponse> response = restTemplate.postForEntity(PG_BASE_URL, entity, PgApiResponse.class);
                PgTransactionResponse data = response.getBody().data();
                
                // 실제 승인 시각을 받을 수 없으므로 현재 시간 사용
                return new PaymentGatewayResult(data.transactionKey(), LocalDateTime.now());
                
            } catch (Exception e) {
                log.error("PG Simulator Request Failed: {}", e.getMessage());
                throw new RuntimeException("PG Simulator Request Failed", e);
            }
        });
    }

    @Override
    public void cancelPayment(String transactionId, BigDecimal amount) {
        log.info("Mocking cancel payment for tx: {}", transactionId);
    }

    @Override
    public PaymentGatewayQueryResult queryPaymentStatus(Long orderId) {
        return circuitBreaker.executeSupplier(() -> {
            String formattedOrderId = String.format("%06d", orderId);
            String url = UriComponentsBuilder.fromHttpUrl(PG_BASE_URL)
                    .queryParam("orderId", formattedOrderId)
                    .toUriString();

            HttpHeaders headers = createHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            try {
                ResponseEntity<PgOrderApiResponse> response = restTemplate.exchange(url, HttpMethod.GET, entity, PgOrderApiResponse.class);
                PgOrderResponse data = response.getBody().data();

                if (data == null || data.transactions() == null || data.transactions().isEmpty()) {
                    throw new RuntimeException("Transaction not found for orderId: " + formattedOrderId);
                }

                // 최신 트랜잭션 정보 추출
                PgTransactionResponse latestTx = data.transactions().get(data.transactions().size() - 1);
                PaymentGatewayStatus status = mapStatus(latestTx.status());

                return new PaymentGatewayQueryResult(
                        status,
                        latestTx.transactionKey(),
                        status == PaymentGatewayStatus.APPROVED ? LocalDateTime.now() : null
                );

            } catch (Exception e) {
                log.error("PG Simulator Query Failed: {}", e.getMessage());
                throw new RuntimeException("PG Simulator Query Failed", e);
            }
        });
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Loopers-UserId", "1");
        return headers;
    }

    private PaymentGatewayStatus mapStatus(String pgStatus) {
        return switch (pgStatus) {
            case "SUCCESS" -> PaymentGatewayStatus.APPROVED;
            case "PENDING" -> PaymentGatewayStatus.PENDING;
            case "FAILED" -> PaymentGatewayStatus.FAILED;
            default -> PaymentGatewayStatus.FAILED;
        };
    }

    // 응답 매핑을 위한 내부 레코드 클래스
    record PgTransactionResponse(String transactionKey, String status, String reason) {}
    record PgApiResponse(Map<String, Object> meta, PgTransactionResponse data) {}
    
    record PgOrderResponse(String orderId, List<PgTransactionResponse> transactions) {}
    record PgOrderApiResponse(Map<String, Object> meta, PgOrderResponse data) {}
}
