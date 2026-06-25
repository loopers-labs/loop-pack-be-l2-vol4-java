package com.loopers.infrastructure.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest
class PaymentRepositoryIntegrationTest {

    private static final String CARD_NO = "1234-5678-9012-3456";

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentJpaRepository paymentJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private PaymentModel payment(Long orderId, Long userId) {
        return PaymentModel.builder()
            .orderId(orderId)
            .userId(userId)
            .amount(78_000)
            .cardType(CardType.SAMSUNG)
            .rawCardNo(CARD_NO)
            .requestedAt(ZonedDateTime.now())
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
}
