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

import java.time.ZonedDateTime;
import java.util.List;

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

    @DisplayName("멈춘 PENDING 결제를 조회할 때, ")
    @Nested
    class FindStuckPending {

        @DisplayName("transactionKey 가 있고 임계 시각보다 먼저 생성된 PENDING 결제는 복구 대상에 포함된다.")
        @Test
        void includesKeyedPendingCreatedBeforeThreshold() {
            // given
            PaymentModel saved = paymentRepository.save(keyedPending("key-1"));

            // when
            List<PaymentModel> stuck = paymentRepository.findStuckPending(ZonedDateTime.now().plusMinutes(1));

            // then
            assertThat(stuck).extracting(PaymentModel::getId).containsExactly(saved.getId());
        }

        @DisplayName("임계 시각 이후에 생성된(아직 처리 중일 수 있는) 결제는 제외한다.")
        @Test
        void excludesRecentlyCreated() {
            // given
            paymentRepository.save(keyedPending("key-1"));

            // when
            List<PaymentModel> stuck = paymentRepository.findStuckPending(ZonedDateTime.now().minusMinutes(1));

            // then
            assertThat(stuck).isEmpty();
        }

        @DisplayName("transactionKey 가 없는(동기 타임아웃) PENDING 결제는 key 로 조회할 수 없어 제외한다.")
        @Test
        void excludesKeylessPending() {
            // given
            paymentRepository.save(PaymentModel.createPending(1L, 100L, 50_000L));

            // when
            List<PaymentModel> stuck = paymentRepository.findStuckPending(ZonedDateTime.now().plusMinutes(1));

            // then
            assertThat(stuck).isEmpty();
        }

        @DisplayName("이미 종착 상태(SUCCESS)인 결제는 복구 대상이 아니므로 제외한다.")
        @Test
        void excludesTerminalStatus() {
            // given
            PaymentModel terminal = keyedPending("key-1");
            terminal.markSuccess("정상 승인");
            paymentRepository.save(terminal);

            // when
            List<PaymentModel> stuck = paymentRepository.findStuckPending(ZonedDateTime.now().plusMinutes(1));

            // then
            assertThat(stuck).isEmpty();
        }

        private PaymentModel keyedPending(String transactionKey) {
            PaymentModel payment = PaymentModel.createPending(1L, 100L, 50_000L);
            payment.assignTransactionKey(transactionKey);
            return payment;
        }
    }
}
