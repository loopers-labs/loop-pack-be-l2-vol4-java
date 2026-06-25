package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayCommand;
import com.loopers.domain.payment.PaymentGatewayResult;
import com.loopers.domain.payment.model.CardType;
import com.loopers.domain.payment.model.Payment;
import com.loopers.domain.payment.model.PaymentStatus;
import com.loopers.domain.payment.service.PaymentService;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PaymentRecoveryIntegrationTest {

    @TestConfiguration
    static class FakeGatewayConfig {
        @Bean
        @Primary
        PaymentGateway fakePaymentGateway() {
            return new PaymentGateway() {
                @Override
                public PaymentGatewayResult requestPayment(PaymentGatewayCommand command) {
                    return new PaymentGatewayResult("TR:recovery", PaymentStatus.PENDING, null);
                }

                @Override
                public PaymentGatewayResult findTransaction(String userId, String transactionKey) {
                    // 폴링 조회 시 PG가 최종 성공 결과를 반환하는 상황
                    return new PaymentGatewayResult(transactionKey, PaymentStatus.SUCCESS, "정상 승인되었습니다.");
                }
            };
        }
    }

    @Autowired
    private PaymentApplicationService paymentApplicationService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("콜백이 유실된 PENDING 결제를, 폴링 복구가 PG 조회로 SUCCESS 확정한다.")
    @Test
    void recoversPendingPayment_viaPolling() {
        // Arrange: PENDING + transactionKey 있는 결제 (콜백 미수신 상황)
        Payment payment = paymentService.initiate(1L, 2L, CardType.SAMSUNG, "1234-5678-9814-1451", 5000L);
        paymentService.assignTransactionKey(payment.getId(), "TR:recovery");

        // Act
        paymentApplicationService.recoverPendingPayments();

        // Assert
        assertThat(paymentService.getPayment(payment.getId()).getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }
}
