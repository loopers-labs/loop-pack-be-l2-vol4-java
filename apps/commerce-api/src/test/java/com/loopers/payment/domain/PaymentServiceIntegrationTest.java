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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
            Payment first = createPendingPayment(ORDER_ID, "20250816:TR:first");
            first.markFailed("20250816:TR:first", PaymentFailureReason.LIMIT_EXCEEDED, "한도초과입니다.", REQUESTED_AT.plusSeconds(5));
            paymentService.savePayment(first);
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

    @DisplayName("결제 요청 가능 여부를 검증할 때")
    @Nested
    class ValidatePaymentRequestable {

        @DisplayName("진행 중인 결제가 있으면, CONFLICT 예외를 던진다.")
        @Test
        void throwsConflict_whenPaymentIsInProgress() {
            // arrange
            paymentService.savePayment(createPendingPayment(ORDER_ID, TRANSACTION_KEY));

            // act & assert
            assertThatThrownBy(() -> paymentService.validatePaymentRequestable(ORDER_ID))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("주문에 결제가 없으면, 예외를 던지지 않는다.")
        @Test
        void doesNotThrow_whenPaymentDoesNotExist() {
            // act & assert
            assertThatCode(() -> paymentService.validatePaymentRequestable(ORDER_ID)).doesNotThrowAnyException();
        }

        @DisplayName("최신 결제가 종료된 결제이면, 예외를 던지지 않는다.")
        @Test
        void doesNotThrow_whenLatestPaymentIsFinalized() {
            // arrange
            Payment payment = createPendingPayment(ORDER_ID, TRANSACTION_KEY);
            payment.markSucceeded(TRANSACTION_KEY, "정상 승인되었습니다.", REQUESTED_AT.plusSeconds(5));
            paymentService.savePayment(payment);

            // act & assert
            assertThatCode(() -> paymentService.validatePaymentRequestable(ORDER_ID)).doesNotThrowAnyException();
        }
    }

    @DisplayName("결제 요청을 시작할 때")
    @Nested
    class StartPayment {

        @DisplayName("결제 요청권을 확보하면, REQUESTING 결제를 저장한다.")
        @Test
        void savesRequestingPayment_whenPaymentCanStart() {
            // arrange
            Payment payment = createRequestingPayment(ORDER_ID);

            // act
            Payment saved = paymentService.startPayment(payment);
            Payment found = paymentService.getPayment(saved.getId());

            // assert
            assertAll(
                () -> assertThat(found.getStatus()).isEqualTo(PaymentStatus.REQUESTING),
                () -> assertThat(found.getOrderId()).isEqualTo(ORDER_ID),
                () -> assertThat(found.getPgTransactionKey()).isNull()
            );
        }

        @DisplayName("진행 중인 결제가 있으면, 새 결제 요청권을 확보할 수 없다.")
        @Test
        void throwsConflict_whenActivePaymentExists() {
            // arrange
            paymentService.startPayment(createRequestingPayment(ORDER_ID));

            // act & assert
            assertThatThrownBy(() -> paymentService.startPayment(createRequestingPayment(ORDER_ID)))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("기존 결제가 종료된 결제이면, 새 결제 요청권을 확보할 수 있다.")
        @Test
        void startsPayment_whenLatestPaymentIsFinalized() {
            // arrange
            Payment payment = createPendingPayment(ORDER_ID, TRANSACTION_KEY);
            payment.markFailed(TRANSACTION_KEY, PaymentFailureReason.LIMIT_EXCEEDED, "한도초과입니다.", REQUESTED_AT.plusSeconds(5));
            paymentService.savePayment(payment);

            // act & assert
            assertThatCode(() -> paymentService.startPayment(createRequestingPayment(ORDER_ID))).doesNotThrowAnyException();
        }
    }

    @DisplayName("복구 대상 결제를 조회할 때")
    @Nested
    class FindRecoverablePayments {

        @DisplayName("확인 시점이 지난 진행 중 결제만 오래된 순서로 반환한다.")
        @Test
        void returnsRecoverablePayments_whenPaymentsAreDue() {
            // arrange
            ZonedDateTime now = REQUESTED_AT.plusMinutes(3);
            Payment requesting = paymentService.savePayment(createRequestingPayment(ORDER_ID));
            Payment pending = paymentService.savePayment(createPendingPayment(OTHER_ORDER_ID, TRANSACTION_KEY));
            Payment unknown = Payment.unknown(
                USER_ID,
                1_351_039_137L,
                AMOUNT,
                CardType.SAMSUNG,
                CARD_NO,
                REQUESTED_AT
            );
            unknown = paymentService.savePayment(unknown);

            Payment notDuePending = createPendingPayment(1_351_039_138L, "20250816:TR:not-due");
            notDuePending.scheduleRecovery(now.plusSeconds(30), "retry later");
            paymentService.savePayment(notDuePending);

            Payment failed = createRequestingPayment(1_351_039_139L);
            failed.markRequestFailed(PaymentFailureReason.PG_UNAVAILABLE, "connect refused", REQUESTED_AT.plusSeconds(1));
            paymentService.savePayment(failed);

            // act
            List<Payment> payments = paymentService.findRecoverablePayments(
                now,
                now.minusSeconds(15),
                now.minusSeconds(30),
                10
            );

            // assert
            assertThat(payments)
                .extracting(Payment::getId)
                .containsExactly(requesting.getId(), pending.getId(), unknown.getId());
        }

        @DisplayName("요청한 청크 크기만큼만 반환한다.")
        @Test
        void returnsPaymentsWithinLimit() {
            // arrange
            ZonedDateTime now = REQUESTED_AT.plusMinutes(3);
            paymentService.savePayment(createRequestingPayment(ORDER_ID));
            paymentService.savePayment(createRequestingPayment(OTHER_ORDER_ID));

            // act
            List<Payment> payments = paymentService.findRecoverablePayments(
                now,
                now.minusSeconds(15),
                now.minusSeconds(30),
                1
            );

            // assert
            assertThat(payments).hasSize(1);
        }
    }

    private Payment createPendingPayment(Long orderId, String transactionKey) {
        return Payment.pending(USER_ID, orderId, AMOUNT, CardType.SAMSUNG, CARD_NO, transactionKey, REQUESTED_AT);
    }

    private Payment createRequestingPayment(Long orderId) {
        return Payment.requesting(USER_ID, orderId, AMOUNT, CardType.SAMSUNG, CARD_NO, REQUESTED_AT);
    }
}
