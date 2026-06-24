package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PaymentRepositoryIntegrationTest {

    private final PaymentRepository paymentRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public PaymentRepositoryIntegrationTest(PaymentRepository paymentRepository, DatabaseCleanUp databaseCleanUp) {
        this.paymentRepository = paymentRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("결제를 저장하고 다시 조회하면, ")
    @Nested
    class SaveAndFind {

        @DisplayName("PENDING 결제가 필드 그대로 round-trip 된다.")
        @Test
        void roundTripsPendingPayment() {
            // given
            PaymentModel saved = paymentRepository.save(PaymentModel.createPending(1L, 100L, 50_000L));

            // when
            PaymentModel found = paymentRepository.findById(saved.getId()).orElseThrow();

            // then
            assertAll(
                () -> assertThat(found.getUserId()).isEqualTo(1L),
                () -> assertThat(found.getOrderId()).isEqualTo(100L),
                () -> assertThat(found.getAmount()).isEqualTo(50_000L),
                () -> assertThat(found.getStatus()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(found.getTransactionKey()).isNull(),
                () -> assertThat(found.getReason()).isNull()
            );
        }
    }
}
