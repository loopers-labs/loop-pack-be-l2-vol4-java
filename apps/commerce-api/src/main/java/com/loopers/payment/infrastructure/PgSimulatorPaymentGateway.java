package com.loopers.payment.infrastructure;

import com.loopers.payment.domain.PaymentFailureReason;
import com.loopers.payment.domain.PaymentGateway;
import com.loopers.payment.domain.PaymentGatewayPaymentCommand;
import com.loopers.payment.domain.PaymentGatewayResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.time.Duration;

@Component
public class PgSimulatorPaymentGateway implements PaymentGateway {

    private static final String USER_ID_HEADER = "X-USER-ID";
    private static final String PAYMENT_PATH = "/api/v1/payments";
    private static final ParameterizedTypeReference<PgSimulatorApiResponse<PgSimulatorPaymentResponse>> PAYMENT_RESPONSE_TYPE = new ParameterizedTypeReference<>() {};

    private final PgSimulatorProperties properties;
    private final RestTemplate restTemplate;

    @Autowired
    public PgSimulatorPaymentGateway(PgSimulatorProperties properties, RestTemplateBuilder restTemplateBuilder) {
        this(
            properties,
            restTemplateBuilder
                .connectTimeout(Duration.ofMillis(properties.connectTimeoutMillis()))
                .readTimeout(Duration.ofMillis(properties.readTimeoutMillis()))
                .build()
        );
    }

    PgSimulatorPaymentGateway(PgSimulatorProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    @Override
    public PaymentGatewayResult requestPayment(PaymentGatewayPaymentCommand command) {
        try {
            return toResult(sendPaymentRequest(command));
        } catch (ResourceAccessException e) {
            return toAccessFailureResult(e);
        } catch (RestClientResponseException e) {
            return PaymentGatewayResult.failed(PaymentFailureReason.PG_REQUEST_FAILED, e.getResponseBodyAsString());
        } catch (RestClientException e) {
            return PaymentGatewayResult.failed(PaymentFailureReason.PG_UNAVAILABLE, e.getMessage());
        }
    }

    private PgSimulatorApiResponse<PgSimulatorPaymentResponse> sendPaymentRequest(PaymentGatewayPaymentCommand command) {
        ResponseEntity<PgSimulatorApiResponse<PgSimulatorPaymentResponse>> response = restTemplate.exchange(
            properties.baseUrl() + PAYMENT_PATH,
            HttpMethod.POST,
            new HttpEntity<>(PgSimulatorPaymentRequest.from(command, properties.callbackUrl()), headers(command.userId())),
            PAYMENT_RESPONSE_TYPE
        );
        return response.getBody();
    }

    private PaymentGatewayResult toResult(PgSimulatorApiResponse<PgSimulatorPaymentResponse> response) {
        if (response == null || !response.isSuccess()) {
            String message = response == null ? null : response.message();
            return PaymentGatewayResult.failed(PaymentFailureReason.PG_REQUEST_FAILED, message);
        }
        return PaymentGatewayResult.accepted(response.data().toTransaction());
    }

    private HttpHeaders headers(Long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(USER_ID_HEADER, String.valueOf(userId));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private PaymentGatewayResult toAccessFailureResult(ResourceAccessException e) {
        if (hasTimeoutCause(e)) {
            return PaymentGatewayResult.unknown(PaymentFailureReason.PG_TIMEOUT, e.getMessage());
        }
        return PaymentGatewayResult.failed(PaymentFailureReason.PG_UNAVAILABLE, e.getMessage());
    }

    private boolean hasTimeoutCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
