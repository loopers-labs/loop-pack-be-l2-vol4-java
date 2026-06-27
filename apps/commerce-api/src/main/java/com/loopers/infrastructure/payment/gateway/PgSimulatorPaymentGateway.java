package com.loopers.infrastructure.payment.gateway;

import com.loopers.domain.payment.gateway.PaymentGateway;
import com.loopers.domain.payment.gateway.PaymentGatewayCommand;
import com.loopers.domain.payment.gateway.PaymentGatewayResult;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.external.ExternalSystemGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class PgSimulatorPaymentGateway implements PaymentGateway {

    private static final String USER_ID_HEADER = "X-USER-ID";
    private static final String UNAVAILABLE_MESSAGE = "일시적으로 결제를 사용할 수 없습니다.";

    private final RestTemplate pgSimulatorRestTemplate;
    private final PgSimulatorProperties properties;

    @Override
    @ExternalSystemGuard(value = UNAVAILABLE_MESSAGE, name = "pg-simulator")
    public PaymentGatewayResult requestPayment(PaymentGatewayCommand.Request command) {
        String url = properties.getBaseUrl() + "/api/v1/payments";
        PgPaymentRequest request = new PgPaymentRequest(
            command.orderId(),
            command.cardType(),
            command.cardNo(),
            command.amount(),
            properties.getCallbackUrl()
        );
        ResponseEntity<PgApiResponse<PgTransactionResponse>> response = pgSimulatorRestTemplate.exchange(
            url,
            HttpMethod.POST,
            new HttpEntity<>(request, headers(command.userId())),
            new ParameterizedTypeReference<>() {}
        );

        PgTransactionResponse data = getData(response);
        return data.toGatewayResult(command.orderId());
    }

    @Override
    @ExternalSystemGuard(value = UNAVAILABLE_MESSAGE, name = "pg-simulator")
    public Optional<PaymentGatewayResult> getPayment(String userId, String transactionKey) {
        try {
            String url = properties.getBaseUrl() + "/api/v1/payments/" + transactionKey;
            ResponseEntity<PgApiResponse<PgTransactionDetailResponse>> response = pgSimulatorRestTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers(userId)),
                new ParameterizedTypeReference<>() {}
            );
            return Optional.of(getData(response).toGatewayResult());
        } catch (HttpClientErrorException.NotFound ignored) {
            return Optional.empty();
        }
    }

    @Override
    @ExternalSystemGuard(value = UNAVAILABLE_MESSAGE, name = "pg-simulator")
    public List<PaymentGatewayResult> getPaymentsByOrderId(String userId, String orderId) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(properties.getBaseUrl() + "/api/v1/payments")
                .queryParam("orderId", orderId)
                .toUriString();
            ResponseEntity<PgApiResponse<PgOrderResponse>> response = pgSimulatorRestTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers(userId)),
                new ParameterizedTypeReference<>() {}
            );
            return getData(response).transactions()
                .stream()
                .map(transaction -> transaction.toGatewayResult(orderId))
                .toList();
        } catch (HttpClientErrorException.NotFound ignored) {
            return List.of();
        }
    }

    private HttpHeaders headers(String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(USER_ID_HEADER, userId);
        return headers;
    }

    private <T> T getData(ResponseEntity<PgApiResponse<T>> response) {
        if (response.getBody() == null || response.getBody().data() == null) {
            throw new CoreException(ErrorType.EXTERNAL_SYSTEM_UNAVAILABLE, UNAVAILABLE_MESSAGE);
        }
        return response.getBody().data();
    }

    private record PgApiResponse<T>(
        PgMetadata meta,
        T data
    ) {}

    private record PgMetadata(
        String result,
        String errorCode,
        String message
    ) {}

    private record PgPaymentRequest(
        String orderId,
        String cardType,
        String cardNo,
        Long amount,
        String callbackUrl
    ) {}

    private record PgTransactionResponse(
        String transactionKey,
        PgTransactionStatus status,
        String reason
    ) {
        private PaymentGatewayResult toGatewayResult(String orderId) {
            return switch (status) {
                case PENDING -> PaymentGatewayResult.pending(transactionKey, orderId);
                case SUCCESS -> PaymentGatewayResult.success(transactionKey, orderId);
                case FAILED -> PaymentGatewayResult.failed(transactionKey, orderId, reason);
            };
        }
    }

    private record PgTransactionDetailResponse(
        String transactionKey,
        String orderId,
        String cardType,
        String cardNo,
        Long amount,
        PgTransactionStatus status,
        String reason
    ) {
        private PaymentGatewayResult toGatewayResult() {
            return new PgTransactionResponse(transactionKey, status, reason).toGatewayResult(orderId);
        }
    }

    private record PgOrderResponse(String orderId, List<PgTransactionResponse> transactions) {}

    private enum PgTransactionStatus {
        PENDING,
        SUCCESS,
        FAILED
    }
}
