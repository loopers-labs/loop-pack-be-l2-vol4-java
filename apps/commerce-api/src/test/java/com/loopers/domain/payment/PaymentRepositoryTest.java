package com.loopers.domain.payment;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PaymentRepositoryTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("결제를 저장할 때,")
    @Nested
    class Save {

        @DisplayName("저장 후 ID가 부여된다.")
        @Test
        void save_assignsId() {
            // arrange
            PaymentModel payment = new PaymentModel(1L, CardType.SAMSUNG, "1234-5678-9012-3456", 10000L);

            // act
            PaymentModel saved = paymentRepository.save(payment);

            // assert
            assertThat(saved.getId()).isNotNull().isPositive();
        }
    }

    @DisplayName("ID로 결제를 조회할 때,")
    @Nested
    class FindById {

        @DisplayName("저장된 결제를 ID로 조회하면, 결제가 반환된다.")
        @Test
        void findById_returnsPayment_whenExists() {
            // arrange
            PaymentModel saved = paymentRepository.save(new PaymentModel(1L, CardType.SAMSUNG, "1234-5678-9012-3456", 10000L));

            // act
            Optional<PaymentModel> result = paymentRepository.findById(saved.getId());

            // assert
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(saved.getId());
        }

        @DisplayName("존재하지 않는 ID로 조회하면, Optional.empty()가 반환된다.")
        @Test
        void findById_returnsEmpty_whenNotExists() {
            // act
            Optional<PaymentModel> result = paymentRepository.findById(999L);

            // assert
            assertThat(result).isEmpty();
        }
    }

    @DisplayName("orderId로 결제를 조회할 때,")
    @Nested
    class FindByOrderId {

        @DisplayName("저장된 결제를 orderId로 조회하면, 결제가 반환된다.")
        @Test
        void findByOrderId_returnsPayment_whenExists() {
            // arrange
            paymentRepository.save(new PaymentModel(1L, CardType.KB, "1234-5678-9012-3456", 20000L));

            // act
            Optional<PaymentModel> result = paymentRepository.findByOrderId(1L);

            // assert
            assertThat(result).isPresent();
            assertThat(result.get().getOrderId()).isEqualTo(1L);
        }

        @DisplayName("존재하지 않는 orderId로 조회하면, Optional.empty()가 반환된다.")
        @Test
        void findByOrderId_returnsEmpty_whenNotExists() {
            // act
            Optional<PaymentModel> result = paymentRepository.findByOrderId(999L);

            // assert
            assertThat(result).isEmpty();
        }
    }

    @DisplayName("transactionKey로 결제를 조회할 때,")
    @Nested
    class FindByTransactionKey {

        @DisplayName("저장된 결제를 transactionKey로 조회하면, 결제가 반환된다.")
        @Test
        void findByTransactionKey_returnsPayment_whenExists() {
            // arrange
            PaymentModel payment = new PaymentModel(1L, CardType.HYUNDAI, "1234-5678-9012-3456", 30000L);
            payment.success("20250625:TR:abc123");
            paymentRepository.save(payment);

            // act
            Optional<PaymentModel> result = paymentRepository.findByTransactionKey("20250625:TR:abc123");

            // assert
            assertThat(result).isPresent();
            assertThat(result.get().getTransactionKey()).isEqualTo("20250625:TR:abc123");
        }
    }

    @DisplayName("PENDING 결제를 기준시각 이전으로 조회할 때,")
    @Nested
    class FindPendingBefore {

        @DisplayName("PENDING 상태이고 기준시각 이전에 생성된 결제가 반환된다.")
        @Test
        void findPendingBefore_returnsPendingPayments() {
            // arrange
            paymentRepository.save(new PaymentModel(1L, CardType.SAMSUNG, "1234-5678-9012-3456", 10000L));
            ZonedDateTime threshold = ZonedDateTime.now().plusSeconds(5);

            // act
            List<PaymentModel> result = paymentRepository.findPendingBefore(threshold);

            // assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus()).isEqualTo(PaymentStatus.PENDING);
        }

        @DisplayName("SUCCESS 또는 FAILED 상태의 결제는 반환되지 않는다.")
        @Test
        void findPendingBefore_excludesNonPendingPayments() {
            // arrange
            PaymentModel successPayment = new PaymentModel(1L, CardType.SAMSUNG, "1234-5678-9012-3456", 10000L);
            successPayment.success("20250625:TR:abc123");
            paymentRepository.save(successPayment);

            PaymentModel failedPayment = new PaymentModel(2L, CardType.KB, "1234-5678-9012-3456", 20000L);
            failedPayment.fail(null, "한도 초과");
            paymentRepository.save(failedPayment);

            ZonedDateTime threshold = ZonedDateTime.now().plusSeconds(5);

            // act
            List<PaymentModel> result = paymentRepository.findPendingBefore(threshold);

            // assert
            assertThat(result).isEmpty();
        }
    }
}
