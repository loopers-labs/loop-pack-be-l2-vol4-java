package com.loopers.application.payment;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderItems;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.product.Money;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.payment.PaymentJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 결제 상태조회({@link PaymentApplicationService#getPayment}) 통합 검증.
 *
 * <p>우리 DB 기준 현재 상태를 그대로 반환하며 PG 를 재호출하지 않는다. 소유권은 {@code Payment} 가 userId 를
 * 보유하지 않으므로 주문에서 도출해 검증한다(본인 외 403, 없으면 404).</p>
 */
@DisplayName("결제 상태조회")
@SpringBootTest
class PaymentStatusQueryIntegrationTest {

    private static final Long OWNER_ID = 7L;
    private static final Long OTHER_ID = 99L;

    @Autowired
    private PaymentApplicationService paymentApplicationService;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private PaymentJpaRepository paymentJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long paymentId;

    @BeforeEach
    void setUp() {
        Order order = orderJpaRepository.save(Order.create(
                OWNER_ID,
                OrderItems.from(List.of(OrderItem.of(1L, "상품", Money.of(5_000L), 1))),
                Money.of(0L)));
        Payment payment = paymentJpaRepository.save(
                Payment.create(order.getId(), CardType.SAMSUNG, "1234-5678-9814-1451", Money.of(5_000L)));
        paymentId = payment.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("본인 결제건은 현재 상태를 그대로 반환한다(요청 직후라 PENDING, 카드번호는 마스킹).")
    @Test
    void owner_getsCurrentState() {
        PaymentInfo.Detail detail = paymentApplicationService.getPayment(paymentId, OWNER_ID);

        assertThat(detail.id()).isEqualTo(paymentId);
        assertThat(detail.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(detail.amount()).isEqualTo(5_000L);
        assertThat(detail.maskedCardNo()).isEqualTo("**** **** **** 1451");
        assertThat(detail.transactionKey()).isNull(); // 아직 PG 접수 전
    }

    @Nested
    @DisplayName("실패")
    class Failure {

        @DisplayName("본인이 아니면 403 FORBIDDEN.")
        @Test
        void notOwner_forbidden() {
            assertThatThrownBy(() -> paymentApplicationService.getPayment(paymentId, OTHER_ID))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.FORBIDDEN));
        }

        @DisplayName("존재하지 않는 결제건은 404 NOT_FOUND.")
        @Test
        void missing_notFound() {
            assertThatThrownBy(() -> paymentApplicationService.getPayment(999_999L, OWNER_ID))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }
    }
}
