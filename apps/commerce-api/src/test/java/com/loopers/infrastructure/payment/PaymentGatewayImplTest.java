package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.FailureReason;
import com.loopers.domain.payment.PaymentGatewayException;
import com.loopers.domain.payment.PaymentGatewayRequest;
import com.loopers.domain.payment.PaymentGatewayResponse;
import com.loopers.domain.payment.TransactionStatus;
import feign.FeignException;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentGatewayImplTest {

    private PaymentGatewayFeignClient feignClient;
    private PaymentGatewayImpl paymentGatewayImpl;
    private PaymentGatewayRequest request;
    private TransactionRequest transactionRequest;

    @BeforeEach
    void setUp() {
        feignClient = mock(PaymentGatewayFeignClient.class);
        PaymentGatewayProperties properties = new PaymentGatewayProperties("http://localhost/callback", 1000, 2000);
        Retry retry = Retry.of("paymentGateway", RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofMillis(100), 2, Duration.ofMillis(300)))
                .retryExceptions(RetryableException.class)
                .build());
        paymentGatewayImpl = new PaymentGatewayImpl(feignClient, properties, retry);

        request = new PaymentGatewayRequest("user1", "order1", CardType.SAMSUNG, "1234-5678-1234-5678", BigDecimal.valueOf(1000));
        transactionRequest = new TransactionRequest(
                request.orderNumber(), request.cardType().name(), request.cardNo(), request.amount().longValueExact(), properties.callbackUrl()
        );
    }

    private RetryableException retryableException() {
        Request feignRequest = Request.create(
                Request.HttpMethod.POST, "/api/v1/payments", Collections.emptyMap(), null, StandardCharsets.UTF_8, null
        );
        return new RetryableException(503, "PG 응답을 받지 못했습니다.", Request.HttpMethod.POST, (Long) null, feignRequest);
    }

    private FeignException.NotFound notFoundException() {
        Request feignRequest = Request.create(
                Request.HttpMethod.GET, "/api/v1/payments", Collections.emptyMap(), null, StandardCharsets.UTF_8, null
        );
        Response response = Response.builder()
                .status(404)
                .request(feignRequest)
                .headers(Collections.emptyMap())
                .build();
        return (FeignException.NotFound) FeignException.errorStatus("PaymentGatewayFeignClient#findTransactionsByOrderId", response);
    }

    @DisplayName("PG 결제를 요청할 때, ")
    @Nested
    class RequestPayment {

        @DisplayName("응답을 받지 못하는 상황이 재시도 횟수만큼 반복되고 조회로도 거래를 확인할 수 없으면, RETRY_FAILED 예외가 발생한다.")
        @Test
        void throwsRetryFailedException_whenAllAttemptsFailWithRetryableException() {
            // given
            when(feignClient.requestPayment(request.userNumber(), transactionRequest)).thenThrow(retryableException());
            when(feignClient.findTransactionsByOrderId(request.userNumber(), request.orderNumber())).thenThrow(notFoundException());

            // when
            PaymentGatewayException result =
                    assertThrows(PaymentGatewayException.class, () -> paymentGatewayImpl.requestPayment(request));

            // then
            assertThat(result.getFailureReason()).isEqualTo(FailureReason.RETRY_FAILED);
        }

        @DisplayName("응답을 받지 못해도 조회에서 기존 거래가 확인되면, 재시도 없이 그 거래 결과를 반환한다.")
        @Test
        void returnsExistingTransaction_whenRetryableExceptionAndExistingTransactionFound() {
            // given
            when(feignClient.requestPayment(request.userNumber(), transactionRequest)).thenThrow(retryableException());

            TransactionsResponse existingResponse = new TransactionsResponse(
                    new TransactionsData(request.orderNumber(), List.of(
                            new TransactionData("existing-txn", TransactionStatus.SUCCESS.name(), null)
                    ))
            );
            when(feignClient.findTransactionsByOrderId(request.userNumber(), request.orderNumber())).thenReturn(existingResponse);

            // when
            PaymentGatewayResponse result = paymentGatewayImpl.requestPayment(request);

            // then
            assertThat(result).isEqualTo(new PaymentGatewayResponse("existing-txn", TransactionStatus.SUCCESS));
        }

        @DisplayName("응답을 받지 못했지만 조회로도 거래를 확인할 수 없으면, 재시도 후 성공한 결과를 반환한다.")
        @Test
        void returnsResponse_whenSecondAttemptSucceedsAfterRetryableException() {
            // given
            TransactionResponse successResponse = new TransactionResponse(
                    new TransactionData("new-txn", TransactionStatus.SUCCESS.name(), null)
            );
            when(feignClient.requestPayment(request.userNumber(), transactionRequest))
                    .thenThrow(retryableException())
                    .thenReturn(successResponse);
            when(feignClient.findTransactionsByOrderId(request.userNumber(), request.orderNumber())).thenThrow(notFoundException());

            // when
            PaymentGatewayResponse result = paymentGatewayImpl.requestPayment(request);

            // then
            assertThat(result).isEqualTo(new PaymentGatewayResponse("new-txn", TransactionStatus.SUCCESS));
        }
    }
}
