package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.CardType;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class PaymentRepositoryIntegrationTest {

    private static final String CARD_NO = "1234-5678-9814-1451";

    @Autowired
    private PaymentRepository paymentRepository;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private PaymentModel savePending(Long orderId, String orderNumber) {
        return paymentRepository.save(
                PaymentModel.pending(orderId, orderNumber, 10L, CardType.SAMSUNG, CARD_NO, 5000L));
    }

    @DisplayName("결제를 조회할 때,")
    @Nested
    class Find {

        @DisplayName("transactionKey 가 부여된 결제를 transactionKey 로 재조회할 수 있다.")
        @Test
        void findsByTransactionKey() {
            // given
            PaymentModel payment = savePending(1L, "20260626000000000001");
            payment.attachTransactionKey("20260626:TR:abc123");
            paymentRepository.save(payment);

            // when
            Optional<PaymentModel> found = paymentRepository.findByTransactionKey("20260626:TR:abc123");

            // then
            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(payment.getId());
        }

        @DisplayName("활성(PENDING/PAID) 결제만 findActiveByOrderId 로 조회되고, FAILED 는 조회되지 않는다.")
        @Test
        void findsActiveByOrderId_excludingTerminalFailed() {
            // given
            PaymentModel active = savePending(100L, "20260626000000000100");
            PaymentModel failed = savePending(200L, "20260626000000000200");
            paymentRepository.transitionToFailed(failed.getId(), "한도초과");

            // when
            Optional<PaymentModel> foundActive = paymentRepository.findActiveByOrderId(100L);
            Optional<PaymentModel> foundFailed = paymentRepository.findActiveByOrderId(200L);

            // then
            assertAll(
                    () -> assertThat(foundActive).isPresent(),
                    () -> assertThat(foundActive.get().getId()).isEqualTo(active.getId()),
                    () -> assertThat(foundFailed).isEmpty()
            );
        }

        @DisplayName("findPendingOlderThan 은 grace(threshold) 이전 PENDING 만 반환한다.")
        @Test
        void findsPendingOlderThanThreshold() {
            // given
            PaymentModel payment = savePending(300L, "20260626000000000300");

            // when
            ZonedDateTime past = ZonedDateTime.now().minusMinutes(1);
            ZonedDateTime future = ZonedDateTime.now().plusMinutes(1);

            // then
            assertAll(
                    () -> assertThat(paymentRepository.findPendingOlderThan(past)).isEmpty(),
                    () -> assertThat(paymentRepository.findPendingOlderThan(future))
                            .extracting(PaymentModel::getId).contains(payment.getId())
            );
        }
    }

    @DisplayName("조건부 UPDATE 로 전이할 때,")
    @Nested
    class ConditionalTransition {

        @DisplayName("PENDING 행을 PAID 로 전이하면 affected=1 이고, 상태와 transactionKey 가 반영된다.")
        @Test
        void transitionsPendingToPaid_affectsOneRow() {
            // given
            PaymentModel payment = savePending(1L, "20260626000000000001");

            // when
            int affected = paymentRepository.transitionToPaid(payment.getId(), "20260626:TR:abc123");

            // then
            PaymentModel reloaded = paymentRepository.findById(payment.getId()).orElseThrow();
            assertAll(
                    () -> assertThat(affected).isEqualTo(1),
                    () -> assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.PAID),
                    () -> assertThat(reloaded.getTransactionKey()).isEqualTo("20260626:TR:abc123")
            );
        }

        @DisplayName("이미 terminal 인 행을 다시 전이하면 affected=0 이고 상태는 그대로다.")
        @Test
        void doesNotAffectTerminalRow() {
            // given
            PaymentModel payment = savePending(1L, "20260626000000000001");
            paymentRepository.transitionToPaid(payment.getId(), "20260626:TR:abc123");

            // when
            int affectedPaid = paymentRepository.transitionToPaid(payment.getId(), "20260626:TR:other");
            int affectedFailed = paymentRepository.transitionToFailed(payment.getId(), "한도초과");

            // then
            PaymentModel reloaded = paymentRepository.findById(payment.getId()).orElseThrow();
            assertAll(
                    () -> assertThat(affectedPaid).isEqualTo(0),
                    () -> assertThat(affectedFailed).isEqualTo(0),
                    () -> assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.PAID),
                    () -> assertThat(reloaded.getTransactionKey()).isEqualTo("20260626:TR:abc123")
            );
        }
    }
}
