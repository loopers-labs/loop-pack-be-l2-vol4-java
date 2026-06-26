package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class PaymentServiceIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private static final Long USER_ID = 1L;
    private static final Long ORDER_ID = 100L;
    private static final String CARD_NO = "1234-5678-9814-1451";
    private static final Long AMOUNT = 5000L;
    private static final String TX_KEY = "20260623:TR:abc123";

    private PaymentModel createPending() {
        return paymentService.create(USER_ID, ORDER_ID, CardType.SAMSUNG, CARD_NO, AMOUNT);
    }

    @DisplayName("결제를 생성하면, id가 부여되고 PENDING 상태로 영속화된다.")
    @Test
    void create_persistsPending() {
        PaymentModel payment = createPending();

        assertAll(
            () -> assertThat(payment.getId()).isNotNull(),
            () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING)
        );
    }

    @DisplayName("트랜잭션 키를 연결하면, 그 키로 결제건을 조회할 수 있다.")
    @Test
    void link_thenFindByTransactionKey() {
        PaymentModel payment = createPending();

        paymentService.linkTransactionKey(payment.getId(), TX_KEY);

        PaymentModel found = paymentService.getByTransactionKey(TX_KEY);
        assertThat(found.getId()).isEqualTo(payment.getId());
    }

    @DisplayName("결과를 반영하면, 해당 상태로 전이된다.")
    @Test
    void applyResult_transitions() {
        PaymentModel payment = createPending();
        paymentService.linkTransactionKey(payment.getId(), TX_KEY);

        paymentService.applyResult(TX_KEY, PaymentStatus.SUCCESS, "정상 승인되었습니다.");

        assertThat(paymentService.getByTransactionKey(TX_KEY).getStatus())
            .isEqualTo(PaymentStatus.SUCCESS);
    }

    @DisplayName("같은 결과를 두 번 반영해도, 멱등하게 첫 결과를 유지한다. (중복 콜백 방어)")
    @Test
    void applyResult_isIdempotent() {
        PaymentModel payment = createPending();
        paymentService.linkTransactionKey(payment.getId(), TX_KEY);
        paymentService.applyResult(TX_KEY, PaymentStatus.SUCCESS, "정상 승인되었습니다.");

        paymentService.applyResult(TX_KEY, PaymentStatus.SUCCESS, "중복 콜백");

        PaymentModel found = paymentService.getByTransactionKey(TX_KEY);
        assertAll(
            () -> assertThat(found.getStatus()).isEqualTo(PaymentStatus.SUCCESS),
            () -> assertThat(found.getReason()).isEqualTo("정상 승인되었습니다.")
        );
    }

    @DisplayName("존재하지 않는 트랜잭션 키로 결과를 반영하면, NOT_FOUND 예외가 발생한다.")
    @Test
    void applyResult_throwsNotFound_whenUnknownKey() {
        CoreException ex = assertThrows(CoreException.class, () ->
            paymentService.applyResult("20260623:TR:none00", PaymentStatus.SUCCESS, "x"));
        assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
    }
}
