package com.loopers.infrastructure.pg;

import com.loopers.config.PgProperties;
import com.loopers.domain.pg.PgGateway;
import com.loopers.domain.pg.PgTransactionResult;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class PgSimulatorGateway implements PgGateway {

    private final RestTemplate pgRestTemplate;
    private final PgProperties pgProperties;

    @Override
    @SuppressWarnings("unchecked")
    public PgTransactionResult request(String userId, String orderId, String cardType, String cardNo, Long amount, String callbackUrl) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-USER-ID", userId);

        Map<String, Object> body = Map.of(
            "orderId", orderId,
            "cardType", cardType,
            "cardNo", cardNo,
            "amount", amount,
            "callbackUrl", callbackUrl
        );

        try {
            Map<String, Object> response = pgRestTemplate.postForObject(
                pgProperties.getSimulatorUrl() + "/api/v1/payments",
                new HttpEntity<>(body, headers),
                Map.class
            );
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            return new PgTransactionResult(
                (String) data.get("transactionKey"),
                (String) data.get("status"),
                (String) data.get("reason")
            );
        } catch (HttpStatusCodeException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "PG 결제 요청 실패: " + e.getResponseBodyAsString());
        } catch (ResourceAccessException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "PG 결제 요청 시간 초과");
        }
    }
}
