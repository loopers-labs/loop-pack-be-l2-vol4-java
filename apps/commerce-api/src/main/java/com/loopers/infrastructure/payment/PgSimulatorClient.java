package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgClient;
import com.loopers.domain.payment.PgPaymentCommand;
import com.loopers.domain.payment.PgPaymentResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Component
public class PgSimulatorClient implements PgClient {

    private static final String HEADER_USER_ID = "X-USER-ID";
    private static final String PAYMENTS_PATH = "/api/v1/payments";

    private final RestTemplate pgRestTemplate;
    private final PgSimulatorProperties pgSimulatorProperties;

    @Override
    public Optional<PgPaymentResult> requestPayment(PgPaymentCommand command) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_USER_ID, String.valueOf(command.userId()));

        PgPaymentRequest body = new PgPaymentRequest(
                String.valueOf(command.orderId()),
                command.cardType().name(),
                command.cardNo(),
                command.amount().longValueExact(),
                pgSimulatorProperties.callbackUrl()
        );

        try {
            PgApiResponse response = pgRestTemplate.postForObject(
                    pgSimulatorProperties.url() + PAYMENTS_PATH,
                    new HttpEntity<>(body, headers),
                    PgApiResponse.class
            );
            if (response == null || response.data() == null) {
                return Optional.empty();
            }
            return Optional.of(new PgPaymentResult(
                    response.data().transactionKey(),
                    PaymentStatus.valueOf(response.data().status())
            ));
        } catch (RestClientException e) {
            log.warn("PG 결제 요청에 실패했습니다. orderId={}", command.orderId(), e);
            return Optional.empty();
        }
    }

    private record PgPaymentRequest(String orderId, String cardType, String cardNo, long amount, String callbackUrl) {
    }

    private record PgApiResponse(PgTransactionData data) {
    }

    private record PgTransactionData(String transactionKey, String status, String reason) {
    }
}
