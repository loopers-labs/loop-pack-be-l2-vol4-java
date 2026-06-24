package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.GatewayCommand;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Component
public class PgClient {

    private static final String USER_ID_HEADER = "X-USER-ID";
    private static final String PAYMENTS_PATH = "/api/v1/payments";

    private final RestClient restClient;
    private final String callbackUrl;

    public PgClient(
        @Value("${payment.pg.base-url}") String baseUrl,
        @Value("${payment.pg.callback-url}") String callbackUrl,
        @Value("${payment.pg.connect-timeout-ms}") int connectTimeoutMs,
        @Value("${payment.pg.read-timeout-ms}") int readTimeoutMs
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(factory)
            .build();
        this.callbackUrl = callbackUrl;
    }

    public String requestPayment(GatewayCommand command) {
        PgPaymentRequest body = new PgPaymentRequest(
            String.format("%06d", command.orderId()),
            command.cardType().name(),
            command.cardNo(),
            command.amount(),
            callbackUrl
        );
        PgApiResponse<PgTransactionResponse> response = restClient.post()
            .uri(PAYMENTS_PATH)
            .header(USER_ID_HEADER, String.valueOf(command.userId()))
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
        if (response == null || response.data() == null) {
            throw new IllegalStateException("PG 응답이 비어 있습니다.");
        }
        return response.data().transactionKey();
    }

    public PgTransactionResponse getTransactionStatus(String transactionKey, Long userId) {
        PgApiResponse<PgTransactionResponse> response = restClient.get()
            .uri(PAYMENTS_PATH + "/{transactionKey}", transactionKey)
            .header(USER_ID_HEADER, String.valueOf(userId))
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
        if (response == null || response.data() == null) {
            throw new IllegalStateException("PG 조회 응답이 비어 있습니다.");
        }
        return response.data();
    }

    /** PG가 404(거래 없음)면 empty, 그 외 실패는 예외로 던진다. */
    public Optional<PgTransactionResponse> findByOrderId(Long orderId, Long userId) {
        try {
            PgApiResponse<PgOrderResponse> response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path(PAYMENTS_PATH)
                    .queryParam("orderId", String.format("%06d", orderId))
                    .build())
                .header(USER_ID_HEADER, String.valueOf(userId))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
            if (response == null || response.data() == null
                || response.data().transactions() == null || response.data().transactions().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(response.data().transactions().get(0));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }
}
