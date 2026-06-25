package com.loopers.tddstudy.infrastructure.order;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class PgPaymentClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String userId;

    public PgPaymentClient(
            @Qualifier("pgRestTemplate") RestTemplate restTemplate,
            @Value("${pg.base-url}") String baseUrl,
            @Value("${pg.user-id}") String userId
    ) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.userId = userId;
    }

    public PgPaymentResponse requestPayment(PgPaymentRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-USER-ID", userId);
        headers.set("Content-Type", "application/json");

        HttpEntity<PgPaymentRequest> entity = new HttpEntity<>(request, headers);

        return restTemplate.postForObject(
                baseUrl + "/api/v1/payments",
                entity,
                PgPaymentResponse.class
        );
    }

    public PgPaymentResponse getPaymentByTransactionKey(String transactionKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-USER-ID", userId);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        return restTemplate.getForObject(
                baseUrl + "/api/v1/payments/" + transactionKey,
                PgPaymentResponse.class
        );
    }
}
