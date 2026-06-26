package com.loopers.infrastructure.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Collections;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;

@ExtendWith(MockitoExtension.class)
class PaymentGatewayImplTest {

    private static final Long ORDER_ID = 100L;
    private static final Long USER_ID = 1L;
    private static final String CALLBACK_URL = "http://localhost:8080/api/v1/payments/callback";

    @Mock
    private PgSimulatorClient pgSimulatorClient;

    @InjectMocks
    private PaymentGatewayImpl paymentGatewayImpl;

    private PaymentModel payment() {
        return PaymentModel.builder()
            .orderId(ORDER_ID)
            .userId(USER_ID)
            .amount(78_000)
            .cardType(CardType.SAMSUNG)
            .rawCardNo("1234-5678-9012-3456")
            .requestedAt(ZonedDateTime.now())
            .build();
    }

    private FeignException feignException(int status) {
        Request request = Request.create(
            Request.HttpMethod.POST,
            "http://localhost:8082/api/v1/payments",
            Collections.emptyMap(),
            null,
            StandardCharsets.UTF_8,
            new RequestTemplate()
        );
        return FeignException.errorStatus(
            "requestPayment",
            feign.Response.builder()
                .status(status)
                .reason("error")
                .request(request)
                .headers(Collections.emptyMap())
                .build()
        );
    }

    @DisplayName("외부 결제 시스템이 실패 응답을 주면 PAYMENT_GATEWAY_ERROR 예외로 변환한다.")
    @Test
    void translatesFeignErrorToCoreException() {
        // arrange
        ReflectionTestUtils.setField(paymentGatewayImpl, "callbackUrl", CALLBACK_URL);
        given(pgSimulatorClient.requestPayment(any(), any())).willThrow(feignException(500));

        // act & assert
        assertThatThrownBy(() -> paymentGatewayImpl.requestPayment(payment()))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.PAYMENT_GATEWAY_ERROR);
    }

    @DisplayName("외부 결제 시스템이 정상 응답을 주면 발급된 거래 식별자를 반환한다.")
    @Test
    void returnsTransactionKey_onSuccess() {
        // arrange
        ReflectionTestUtils.setField(paymentGatewayImpl, "callbackUrl", CALLBACK_URL);
        PgSimulatorDto.ApiResponse<PgSimulatorDto.TransactionResponse> response = new PgSimulatorDto.ApiResponse<>(
            new PgSimulatorDto.ApiResponse.Metadata("SUCCESS", null, null),
            new PgSimulatorDto.TransactionResponse("TX-0001", "PENDING", null)
        );
        given(pgSimulatorClient.requestPayment(any(), any())).willReturn(response);

        // act
        String transactionKey = paymentGatewayImpl.requestPayment(payment());

        // assert
        assertThat(transactionKey).isEqualTo("TX-0001");
    }
}
