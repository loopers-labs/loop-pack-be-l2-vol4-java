package com.loopers.payment.domain;

import com.loopers.shared.error.CoreException;
import com.loopers.shared.error.ErrorType;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class PaymentServiceIntegrationTest {

    private static final Long USER_ID = 1L;
    private static final Long ORDER_ID = 1_351_039_135L;
    private static final Long OTHER_ORDER_ID = 1_351_039_136L;
    private static final long AMOUNT = 5_000L;
    private static final String CARD_NO = "1234-5678-9814-1451";
    private static final String TRANSACTION_KEY = "20250816:TR:9577c5";
    private static final ZonedDateTime REQUESTED_AT = ZonedDateTime.parse("2026-06-25T10:00:00+09:00");

    private final PaymentService paymentService;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    PaymentServiceIntegrationTest(
        PaymentService paymentService,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.paymentService = paymentService;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("결제를 저장할 때")
    @Nested
    class SavePayment {

        @DisplayName("결제가 주어지면, 결제 정보를 저장한다.")
        @Test
        void savesPayment_whenPaymentIsProvided() {
            // arrange
            Payment payment = createPendingPayment(ORDER_ID, TRANSACTION_KEY);

            // act
            Payment saved = paymentService.savePayment(payment);
            Payment found = paymentService.getPayment(saved.getId());

            // assert
            assertAll(
                () -> assertThat(found.getId()).isEqualTo(saved.getId()),
                () -> assertThat(found.getUserId()).isEqualTo(USER_ID),
                () -> assertThat(found.getOrderId()).isEqualTo(ORDER_ID),
                () -> assertThat(found.getAmount()).isEqualTo(AMOUNT),
                () -> assertThat(found.getStatus()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(found.getPgTransactionKey()).isEqualTo(TRANSACTION_KEY),
                () -> assertThat(found.getMaskedCardNo()).isEqualTo("1234-****-****-1451")
            );
        }
    }

    @DisplayName("PG 거래 키로 결제를 조회할 때")
    @Nested
    class GetPaymentByPgTransactionKey {

        @DisplayName("PG 거래 키에 해당하는 결제가 있으면, 결제를 반환한다.")
        @Test
        void returnsPayment_whenPgTransactionKeyExists() {
            // arrange
            Payment saved = paymentService.savePayment(createPendingPayment(ORDER_ID, TRANSACTION_KEY));

            // act
            Payment found = paymentService.getPaymentByPgTransactionKey(TRANSACTION_KEY);

            // assert
            assertThat(found.getId()).isEqualTo(saved.getId());
        }

        @DisplayName("PG 거래 키에 해당하는 결제가 없으면, NOT_FOUND 예외를 던진다.")
        @Test
        void throwsNotFound_whenPgTransactionKeyDoesNotExist() {
            // act & assert
            assertThatThrownBy(() -> paymentService.getPaymentByPgTransactionKey("20250816:TR:not-found"))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("주문의 최신 결제를 조회할 때")
    @Nested
    class FindLatestPaymentByOrderId {

        @DisplayName("주문에 결제가 있으면, 가장 나중에 저장된 결제를 반환한다.")
        @Test
        void returnsLatestPayment_whenPaymentsExist() {
            // arrange
            paymentService.savePayment(createPendingPayment(ORDER_ID, "20250816:TR:first"));
            Payment latest = paymentService.savePayment(createPendingPayment(ORDER_ID, "20250816:TR:second"));
            paymentService.savePayment(createPendingPayment(OTHER_ORDER_ID, "20250816:TR:other"));

            // act
            Optional<Payment> found = paymentService.findLatestPaymentByOrderId(ORDER_ID);

            // assert
            assertThat(found)
                .isPresent()
                .get()
                .satisfies(payment -> assertAll(
                    () -> assertThat(payment.getId()).isEqualTo(latest.getId()),
                    () -> assertThat(payment.getPgTransactionKey()).isEqualTo("20250816:TR:second")
                ));
        }

        @DisplayName("주문에 결제가 없으면, 빈 값을 반환한다.")
        @Test
        void returnsEmpty_whenPaymentDoesNotExist() {
            // act
            Optional<Payment> found = paymentService.findLatestPaymentByOrderId(ORDER_ID);

            // assert
            assertThat(found).isEmpty();
        }
    }

    private Payment createPendingPayment(Long orderId, String transactionKey) {
        return Payment.pending(USER_ID, orderId, AMOUNT, CardType.SAMSUNG, CARD_NO, transactionKey, REQUESTED_AT);
    }
}
