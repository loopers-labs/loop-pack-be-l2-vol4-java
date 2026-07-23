package com.loopers.tddstudy.infrastructure.order;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class PgPaymentClient {
    private final RestTemplate restTemplate;
    private final String pgBaseUrl;
    private final String userId;

    public PgPaymentClient(@Qualifier("pgRestTemplate") RestTemplate restTemplate,
                           @Value("${pg.base-url}") String pgBaseUrl,
                           @Value("${pg.user-id}") String userId) {
        this.restTemplate = restTemplate;
        this.pgBaseUrl = pgBaseUrl;
        this.userId = userId;
    }

    public PgPaymentResponse requestPayment(PgPaymentRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-USER-ID", userId);
        headers.set("Content-Type", "application/json");

        HttpEntity<PgPaymentRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<PgApiResponse<PgPaymentResponse>> response = restTemplate.exchange(
                pgBaseUrl + "/api/v1/payments",
                HttpMethod.POST,
                entity,
                new ParameterizedTypeReference<>() {}
        );

        return response.getBody() != null ? response.getBody().data() : null;
    }
}
