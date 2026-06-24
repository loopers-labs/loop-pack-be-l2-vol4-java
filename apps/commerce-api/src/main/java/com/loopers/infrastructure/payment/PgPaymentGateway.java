package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayCommand;
import com.loopers.domain.payment.PaymentGatewayResult;
import com.loopers.domain.payment.PaymentGatewayTransaction;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class PgPaymentGateway implements PaymentGateway {

    private static final String PAYMENTS_PATH = "/api/v1/payments";
    private static final String USER_ID_HEADER = "X-USER-ID";

    private final RestTemplate pgRestTemplate;
    private final PgClientProperties properties;

    @Override
    public PaymentGatewayResult requestPayment(PaymentGatewayCommand command) {
        PgPaymentDto.Request body = new PgPaymentDto.Request(
            encodeOrderId(command.orderId()),
            command.cardType(),
            command.cardNo(),
            command.amount(),
            properties.callbackUrl()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(USER_ID_HEADER, String.valueOf(command.userId()));

        ResponseEntity<PgPaymentDto.ApiResponse<PgPaymentDto.Response>> response = pgRestTemplate.exchange(
            properties.baseUrl() + PAYMENTS_PATH,
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            new ParameterizedTypeReference<PgPaymentDto.ApiResponse<PgPaymentDto.Response>>() {}
        );

        PgPaymentDto.Response data = extractData(response.getBody());
        return new PaymentGatewayResult(data.transactionKey(), PaymentStatus.valueOf(data.status()));
    }

    @Override
    public PaymentGatewayTransaction getTransaction(Long userId, String transactionKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(USER_ID_HEADER, String.valueOf(userId));

        ResponseEntity<PgPaymentDto.ApiResponse<PgPaymentDto.TransactionDetail>> response = pgRestTemplate.exchange(
            properties.baseUrl() + PAYMENTS_PATH + "/" + transactionKey,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            new ParameterizedTypeReference<PgPaymentDto.ApiResponse<PgPaymentDto.TransactionDetail>>() {}
        );

        PgPaymentDto.TransactionDetail data = extractData(response.getBody());
        return new PaymentGatewayTransaction(PaymentStatus.valueOf(data.status()), data.reason());
    }

    private String encodeOrderId(Long orderId) {
        return String.format("%06d", orderId);
    }

    private <T> T extractData(PgPaymentDto.ApiResponse<T> body) {
        if (body == null || body.data() == null) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "PG 응답 본문이 비어있습니다.");
        }
        return body.data();
    }
}
