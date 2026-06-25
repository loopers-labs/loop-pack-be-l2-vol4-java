package com.loopers.application.payment;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderItems;
import com.loopers.domain.order.OrderStatus;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PG 콜백 결과 확정 — 금액 교차검증({@link PaymentResultApplier#apply}) 통합 검증.
 *
 * <p>성공 콜백은 통지된 금액이 우리 보관 금액과 일치할 때만 `SUCCESS`로 확정하고 주문을 `PAID`로 만든다.
 * 금액이 불일치하면 확정을 거부(`BAD_REQUEST`)하고 결제는 `PENDING`, 주문은 `CREATED`로 남겨 돈 사고를 막는다.</p>
 */
@DisplayName("결제 콜백 — 금액 교차검증")
@SpringBootTest
class PaymentCallbackIntegrationTest {

    private static final Long USER_ID = 7L;
    private static final long AMOUNT = 5_000L;
    private static final String TX_KEY = "20250816:TR:test1";

    @Autowired
    private PaymentApplicationService paymentApplicationService;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private PaymentJpaRepository paymentJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long orderId;
    private Long paymentId;

    @BeforeEach
    void setUp() {
        Order order = orderJpaRepository.save(Order.create(
                USER_ID,
                OrderItems.from(List.of(OrderItem.of(1L, "상품", Money.of(AMOUNT), 1))),
                Money.of(0L)));
        orderId = order.getId();

        Payment payment = Payment.create(orderId, CardType.SAMSUNG, "1234-5678-9814-1451", Money.of(AMOUNT));
        payment.assignTransactionKey(TX_KEY);
        paymentId = paymentJpaRepository.save(payment).getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("성공 콜백의 금액이 일치하면 결제는 SUCCESS, 주문은 PAID 로 확정된다.")
    @Test
    void successCallback_amountMatches_confirms() {
        paymentApplicationService.handleCallback(
                new PaymentCriteria.Callback(TX_KEY, PaymentStatus.SUCCESS, AMOUNT, null));

        assertThat(paymentJpaRepository.findById(paymentId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.SUCCESS);
        assertThat(orderJpaRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
    }

    @DisplayName("성공 콜백의 금액이 불일치하면 확정을 거부(BAD_REQUEST)하고 결제는 PENDING, 주문은 CREATED 로 남는다.")
    @Test
    void successCallback_amountMismatch_rejected() {
        assertThatThrownBy(() -> paymentApplicationService.handleCallback(
                new PaymentCriteria.Callback(TX_KEY, PaymentStatus.SUCCESS, AMOUNT + 9_999L, null)))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));

        assertThat(paymentJpaRepository.findById(paymentId).orElseThrow().getStatus())
                .as("확정 거부 — PENDING 유지").isEqualTo(PaymentStatus.PENDING);
        assertThat(orderJpaRepository.findById(orderId).orElseThrow().getStatus())
                .as("주문은 PAID 가 되면 안 된다").isEqualTo(OrderStatus.CREATED);
    }

    @DisplayName("실패 콜백은 금액과 무관하게 FAILED 로 확정된다(금액 검증은 성공 확정에만 적용).")
    @Test
    void failedCallback_ignoresAmount() {
        paymentApplicationService.handleCallback(
                new PaymentCriteria.Callback(TX_KEY, PaymentStatus.FAILED, AMOUNT + 1L, "한도초과"));

        assertThat(paymentJpaRepository.findById(paymentId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.FAILED);
        assertThat(orderJpaRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CREATED);
    }
}
