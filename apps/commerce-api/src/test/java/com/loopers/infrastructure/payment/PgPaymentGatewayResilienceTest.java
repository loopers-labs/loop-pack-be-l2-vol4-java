package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayCommand;
import com.loopers.domain.payment.PaymentGatewayResult;
import com.loopers.domain.payment.model.CardType;
import com.loopers.domain.payment.model.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PgPaymentGatewayResilienceTest {

    @TestConfiguration
    static class FailingPgClientConfig {
        @Bean
        @Primary
        PgClient failingPgClient() {
            return (userId, request) -> {
                throw new RuntimeException("PG 장애 시뮬레이션");
            };
        }
    }

    @Autowired
    private PaymentGateway paymentGateway;

    @DisplayName("PG 호출이 실패하면, fallback이 트랜잭션 키 없이 PENDING을 반환한다.")
    @Test
    void returnsPendingFallback_whenPgFails() {
        // Arrange
        PaymentGatewayCommand command = new PaymentGatewayCommand(
            "1", "123456789012", CardType.SAMSUNG, "1234-5678-9814-1451", 5000L,
            "http://localhost:8080/api/v1/payments/callback");

        // Act
        PaymentGatewayResult result = paymentGateway.requestPayment(command);

        // Assert
        assertThat(result.transactionKey()).isNull();
        assertThat(result.status()).isEqualTo(PaymentStatus.PENDING);
    }
}
