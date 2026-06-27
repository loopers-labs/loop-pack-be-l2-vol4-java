package com.loopers.infrastructure.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRequestResult;

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

    @DisplayName("외부 결제 시스템이 접수에 성공하면 발급된 거래 식별자를 담은 ACCEPTED 결과를 반환한다.")
    @Test
    void returnsAcceptedResult_onSuccess() {
        // arrange
        ReflectionTestUtils.setField(paymentGatewayImpl, "callbackUrl", CALLBACK_URL);
        PgSimulatorDto.ApiResponse<PgSimulatorDto.TransactionResponse> response = new PgSimulatorDto.ApiResponse<>(
            new PgSimulatorDto.ApiResponse.Metadata("SUCCESS", null, null),
            new PgSimulatorDto.TransactionResponse("TX-0001", "PENDING", null)
        );
        given(pgSimulatorClient.requestPayment(any(), any())).willReturn(response);

        // act
        PaymentRequestResult result = paymentGatewayImpl.requestPayment(payment());

        // assert
        assertAll(
            () -> assertThat(result.outcome()).isEqualTo(PaymentRequestResult.Outcome.ACCEPTED),
            () -> assertThat(result.transactionKey()).isEqualTo("TX-0001")
        );
    }
}
