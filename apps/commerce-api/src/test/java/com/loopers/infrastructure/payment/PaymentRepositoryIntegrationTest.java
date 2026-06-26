package com.loopers.infrastructure.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest
class PaymentRepositoryIntegrationTest {

    private static final String CARD_NO = "1234-5678-9012-3456";

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentJpaRepository paymentJpaRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private int confirmIfUnresolvedInTransaction(Long paymentId, PaymentStatus status, String reason) {
        return new TransactionTemplate(transactionManager)
            .execute(transactionStatus -> paymentRepository.confirmIfUnresolved(paymentId, status, reason));
    }

    private PaymentModel payment(Long orderId, Long userId) {
        return payment(orderId, userId, ZonedDateTime.now());
    }

    private PaymentModel payment(Long orderId, Long userId, ZonedDateTime requestedAt) {
        return PaymentModel.builder()
            .orderId(orderId)
            .userId(userId)
            .amount(78_000)
            .cardType(CardType.SAMSUNG)
            .rawCardNo(CARD_NO)
            .requestedAt(requestedAt)
            .build();
    }

    @DisplayName("결제를 저장할 때,")
    @Nested
    class Save {

        @DisplayName("식별자가 부여되어 영속화된다.")
        @Test
        void persistsPayment() {
            // arrange
            PaymentModel payment = payment(1L, 2L);

            // act
            PaymentModel saved = paymentRepository.save(payment);

            // assert
            assertAll(
                () -> assertThat(saved.getId()).isPositive(),
                () -> assertThat(paymentJpaRepository.findById(saved.getId())).isPresent()
            );
        }

        @DisplayName("같은 주문으로 두 번째 결제를 저장하면 유일 제약 위반 예외가 발생한다.")
        @Test
        void throwsDataIntegrityViolation_whenOrderIdDuplicated() {
            // arrange
            paymentRepository.save(payment(1L, 2L));

            // act & assert
            assertThatThrownBy(() -> paymentJpaRepository.saveAndFlush(payment(1L, 3L)))
                .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @DisplayName("주문 식별자로 결제를 조회할 때,")
    @Nested
    class GetByOrderId {

        @DisplayName("결제가 있으면 해당 결제를 반환한다.")
        @Test
        void returnsPayment_whenExists() {
            // arrange
            PaymentModel saved = paymentRepository.save(payment(1L, 2L));

            // act
            PaymentModel found = paymentRepository.getByOrderId(1L);

            // assert
            assertThat(found.getId()).isEqualTo(saved.getId());
        }

        @DisplayName("결제가 없으면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenAbsent() {
            // act & assert
            assertThatThrownBy(() -> paymentRepository.getByOrderId(999L))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("주문별 결제 존재를 확인할 때,")
    @Nested
    class ExistsByOrderId {

        @DisplayName("결제가 있으면 true를 반환한다.")
        @Test
        void returnsTrue_whenPaymentExists() {
            // arrange
            paymentRepository.save(payment(1L, 2L));

            // act & assert
            assertThat(paymentRepository.existsByOrderId(1L)).isTrue();
        }

        @DisplayName("결제가 없으면 false를 반환한다.")
        @Test
        void returnsFalse_whenPaymentAbsent() {
            // act & assert
            assertThat(paymentRepository.existsByOrderId(999L)).isFalse();
        }
    }

    @DisplayName("미해결 결제를 조건부로 확정할 때,")
    @Nested
    class ConfirmIfUnresolved {

        @DisplayName("PENDING이면 종료 상태로 전이하고 변경된 행 수 1을 반환한다.")
        @Test
        void transitions_whenPending() {
            // arrange
            PaymentModel saved = paymentRepository.save(payment(1L, 2L));

            // act
            int affectedRows = confirmIfUnresolvedInTransaction(saved.getId(), PaymentStatus.SUCCESS, null);

            // assert
            assertAll(
                () -> assertThat(affectedRows).isEqualTo(1),
                () -> assertThat(paymentJpaRepository.findById(saved.getId()).orElseThrow().getStatus())
                    .isEqualTo(PaymentStatus.SUCCESS)
            );
        }

        @DisplayName("STUCK으로 격리된 결제도 다시 종료 상태로 복구할 수 있다.")
        @Test
        void transitions_whenStuck() {
            // arrange
            PaymentModel payment = payment(1L, 2L);
            payment.markStuck();
            PaymentModel saved = paymentRepository.save(payment);

            // act
            int affectedRows = confirmIfUnresolvedInTransaction(saved.getId(), PaymentStatus.SUCCESS, null);

            // assert
            assertAll(
                () -> assertThat(affectedRows).isEqualTo(1),
                () -> assertThat(paymentJpaRepository.findById(saved.getId()).orElseThrow().getStatus())
                    .isEqualTo(PaymentStatus.SUCCESS)
            );
        }

        @DisplayName("이미 종료된 결제면 변경된 행이 없고 기존 상태가 유지된다.")
        @Test
        void doesNothing_whenAlreadyTerminal() {
            // arrange
            PaymentModel saved = paymentRepository.save(payment(1L, 2L));
            confirmIfUnresolvedInTransaction(saved.getId(), PaymentStatus.SUCCESS, null);

            // act
            int affectedRows = confirmIfUnresolvedInTransaction(saved.getId(), PaymentStatus.FAILED, "뒤늦은 실패");

            // assert
            assertAll(
                () -> assertThat(affectedRows).isEqualTo(0),
                () -> assertThat(paymentJpaRepository.findById(saved.getId()).orElseThrow().getStatus())
                    .isEqualTo(PaymentStatus.SUCCESS)
            );
        }
    }

    @DisplayName("유예 시간이 지난 PENDING 결제를 조회할 때,")
    @Nested
    class FindPendingRequestedBefore {

        @DisplayName("기준 시각 이전에 접수된 PENDING만 반환하고 최근 건·STUCK·종료 건은 제외한다.")
        @Test
        void returnsOnlyAgedPending() {
            // arrange
            ZonedDateTime now = ZonedDateTime.now();
            PaymentModel agedPending = paymentRepository.save(payment(1L, 2L, now.minusSeconds(10)));
            paymentRepository.save(payment(2L, 2L, now.minusSeconds(1)));
            PaymentModel agedStuck = payment(3L, 2L, now.minusSeconds(10));
            agedStuck.markStuck();
            paymentRepository.save(agedStuck);

            // act
            List<PaymentModel> reconcilable = paymentRepository.findPendingRequestedBefore(now.minusSeconds(5));

            // assert
            assertThat(reconcilable).extracting(PaymentModel::getId).containsExactly(agedPending.getId());
        }
    }
}
