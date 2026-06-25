package com.loopers.payment.infrastructure;

import com.loopers.common.domain.Money;
import com.loopers.payment.domain.Payment;
import com.loopers.payment.domain.PaymentRepository;
import com.loopers.payment.domain.PgProvider;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PaymentRepositoryIntegrationTest {

    private static final Long USER_ID = 1L;
    private static final long AMOUNT = 29_000L;

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

    private Payment pending(String orderNumber) {
        return Payment.create(USER_ID, orderNumber, Money.of(AMOUNT));
    }

    @Test
    @DisplayName("findStalePendingWithoutKey 는 키 없는 PENDING 중 기준 시각 이전 생성분만 반환한다")
    void givenMixedPayments_whenFindStalePendingWithoutKey_thenReturnsOnlyKeylessPending() {
        // Arrange: 키 없는 PENDING / 키 있는 PENDING / 키 없는 SUCCESS 를 섞어 저장
        paymentRepository.save(pending("20260625-000001"));

        Payment withKey = pending("20260625-000002");
        withKey.assignTransaction("tx-key", PgProvider.TOSS);
        paymentRepository.save(withKey);

        Payment succeeded = pending("20260625-000003");
        succeeded.markSuccess();
        paymentRepository.save(succeeded);

        // Act: 기준 시각을 미래로 두면 방금 저장한 행이 모두 "경과" 대상
        List<Payment> stale = paymentRepository.findStalePendingWithoutKey(ZonedDateTime.now().plusMinutes(1));

        // Assert: 키 없는 PENDING 한 건만
        assertThat(stale)
                .extracting(Payment::getOrderNumber)
                .containsExactly("20260625-000001");
    }

    @Test
    @DisplayName("findStalePendingWithoutKey 는 기준 시각 이후 생성분은 제외한다(created_at 비교 검증)")
    void givenRecentPayment_whenFindStalePendingWithoutKeyBeforePast_thenEmpty() {
        paymentRepository.save(pending("20260625-000001"));

        List<Payment> stale = paymentRepository.findStalePendingWithoutKey(ZonedDateTime.now().minusMinutes(1));

        assertThat(stale).isEmpty();
    }

    @Test
    @DisplayName("같은 order_number 로 결제를 두 번 저장하면 유니크 제약 위반이 발생한다")
    void givenSameOrderNumber_whenSaveTwice_thenThrowsDataIntegrityViolation() {
        String orderNumber = "20260625-000001";
        paymentRepository.save(pending(orderNumber));

        assertThatThrownBy(() -> paymentRepository.save(pending(orderNumber)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
