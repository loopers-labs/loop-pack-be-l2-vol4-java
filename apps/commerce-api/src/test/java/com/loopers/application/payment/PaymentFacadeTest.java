package com.loopers.application.payment;

import com.loopers.domain.order.FakeOrderRepository;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.FakePaymentGateway;
import com.loopers.domain.payment.FakePaymentGatewayRouter;
import com.loopers.domain.payment.FakePaymentRepository;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgPermanentException;
import com.loopers.domain.payment.PgResponse;
import com.loopers.domain.payment.PgStatus;
import com.loopers.domain.payment.PgUnknownException;
import com.loopers.domain.payment.event.PaymentFailedEvent;
import com.loopers.domain.payment.event.PaymentSucceededEvent;
import com.loopers.domain.shared.Money;
import com.loopers.domain.shared.Quantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * PaymentFacade 단위 테스트. 7가지 핵심 시나리오 검증.
 * 실제 Resilience4j 동작은 통합 테스트에서, 여기선 PaymentGateway behavior 만 시뮬레이션.
 */
class PaymentFacadeTest {

    private static final Long USER_ID = 1L;
    private static final String VALID_CARD = "1234-5678-9814-1451";

    private OrderRepository orderRepository;
    private PaymentRepository paymentRepository;
    private ApplicationEventPublisher eventPublisher;
    private FakePaymentGateway gateway;
    private FakePaymentGatewayRouter router;
    private PaymentFacade facade;

    @BeforeEach
    void setUp() {
        orderRepository = new FakeOrderRepository();
        paymentRepository = new FakePaymentRepository();
        eventPublisher = mock(ApplicationEventPublisher.class);
        gateway = new FakePaymentGateway();
        router = new FakePaymentGatewayRouter(gateway);
        PaymentService paymentService = new PaymentService(paymentRepository, eventPublisher);
        facade = new PaymentFacade(orderRepository, paymentService, router);
    }

    private Order saveOrder(Long userId, long amount) {
        Order order = Order.create(
            userId,
            List.of(OrderItem.of(1L, "상품", Money.of(amount), Quantity.of(1))),
            null, null
        );
        return orderRepository.save(order);
    }

    private PaymentCommand commandFor(Long orderId) {
        return new PaymentCommand(orderId, CardType.SAMSUNG, VALID_CARD);
    }

    @DisplayName("정상 결제 요청 시, Payment 는 IN_PROGRESS 로 전이되고 사용자에겐 PROCESSING 응답을 돌려준다.")
    @Test
    void requestPayment_normal_marksInProgress() {
        Order order = saveOrder(USER_ID, 5_000L);

        PaymentInfo info = facade.requestPayment(USER_ID, commandFor(order.getId()));

        assertThat(info.userStatus()).isEqualTo("PROCESSING");
        Payment payment = paymentRepository.find(info.paymentId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
        assertThat(payment.getTransactionKey()).isEqualTo("TX-" + order.getId());
        assertThat(payment.getCardLastFour()).isEqualTo("1451");
    }

    @DisplayName("PgPermanentException + 도메인 사유(잘못된 카드) → FAILED_RETRYABLE 응답 + 이벤트 발행.")
    @Test
    void requestPayment_recoverableReason_marksFailedRetryable() {
        Order order = saveOrder(USER_ID, 5_000L);
        gateway.setRequestBehavior(req -> {
            throw new PgPermanentException("잘못된 카드");
        });

        PaymentInfo info = facade.requestPayment(USER_ID, commandFor(order.getId()));

        assertThat(info.userStatus()).isEqualTo("FAILED_RETRYABLE");
        Payment payment = paymentRepository.find(info.paymentId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(eventPublisher).publishEvent(any(PaymentFailedEvent.class));
    }

    @DisplayName("PgPermanentException + 시스템 사유(라우팅 불가) → FAILED 응답 + 이벤트 발행.")
    @Test
    void requestPayment_terminalReason_marksFailed() {
        Order order = saveOrder(USER_ID, 5_000L);
        gateway.setRequestBehavior(req -> {
            throw new PgPermanentException("PG 시스템 오류");
        });

        PaymentInfo info = facade.requestPayment(USER_ID, commandFor(order.getId()));

        assertThat(info.userStatus()).isEqualTo("FAILED");
        verify(eventPublisher).publishEvent(any(PaymentFailedEvent.class));
    }

    @DisplayName("PgUnknownException 시 Payment 는 UNKNOWN 으로 두고 이벤트는 발행하지 않는다 (폴링이 확정).")
    @Test
    void requestPayment_unknownException_marksUnknown_withoutEvent() {
        Order order = saveOrder(USER_ID, 5_000L);
        gateway.setRequestBehavior(req -> {
            throw new PgUnknownException("read timeout");
        });

        PaymentInfo info = facade.requestPayment(USER_ID, commandFor(order.getId()));

        assertThat(info.userStatus()).isEqualTo("PROCESSING");
        Payment payment = paymentRepository.find(info.paymentId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.UNKNOWN);
        verify(eventPublisher, never()).publishEvent(any(PaymentSucceededEvent.class));
        verify(eventPublisher, never()).publishEvent(any(PaymentFailedEvent.class));
    }

    @DisplayName("라우터가 사용 가능한 PG 없음을 알리면 (PgPermanentException) Payment FAILED + 이벤트 발행.")
    @Test
    void requestPayment_allGatewaysDown_marksFailed() {
        Order order = saveOrder(USER_ID, 5_000L);
        router.simulateAllGatewaysDown();

        PaymentInfo info = facade.requestPayment(USER_ID, commandFor(order.getId()));

        assertThat(info.userStatus()).isEqualTo("FAILED");
        Payment payment = paymentRepository.find(info.paymentId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(eventPublisher).publishEvent(any(PaymentFailedEvent.class));
    }

    @DisplayName("같은 주문에 진행 중인 결제가 있으면 CONFLICT (멱등 보호).")
    @Test
    void requestPayment_throwsConflict_whenAlreadyInProgress() {
        Order order = saveOrder(USER_ID, 5_000L);
        facade.requestPayment(USER_ID, commandFor(order.getId()));   // 첫 결제 → IN_PROGRESS

        CoreException ex = assertThrows(CoreException.class,
            () -> facade.requestPayment(USER_ID, commandFor(order.getId())));
        assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
    }

    @DisplayName("RECOVERABLE 실패 (한도초과) 후엔 같은 주문에 새 결제 시도가 가능해야 한다 (사용자 재시도 보장).")
    @Test
    void requestPayment_allowsRetry_afterRecoverableFailure() {
        Order order = saveOrder(USER_ID, 5_000L);
        // 첫 시도 — RECOVERABLE 로 실패
        gateway.setRequestBehavior(req -> {
            throw new com.loopers.domain.payment.PgPermanentException("한도초과입니다.");
        });
        PaymentInfo first = facade.requestPayment(USER_ID, commandFor(order.getId()));
        assertThat(first.userStatus()).isEqualTo("FAILED_RETRYABLE");

        // 두 번째 시도 — 정상 흐름. 첫 결제가 FAILED 라 멱등 차단 X.
        gateway.setRequestBehavior(req -> new PgResponse("TX-RETRY", PgStatus.PENDING, null));
        PaymentInfo second = facade.requestPayment(USER_ID, commandFor(order.getId()));
        assertThat(second.userStatus()).isEqualTo("PROCESSING");
        assertThat(second.paymentId()).isNotEqualTo(first.paymentId());  // 새 결제 시도
    }

    @DisplayName("다른 사용자의 주문에 결제 요청하면 NOT_FOUND (정보 노출 차단).")
    @Test
    void requestPayment_throwsNotFound_whenOrderOwnedByOther() {
        Order order = saveOrder(USER_ID, 5_000L);

        CoreException ex = assertThrows(CoreException.class,
            () -> facade.requestPayment(999L, commandFor(order.getId())));
        assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
    }

    @DisplayName("이미 PAID 상태인 주문에 결제 요청하면 CONFLICT.")
    @Test
    void requestPayment_throwsConflict_whenOrderAlreadyPaid() {
        Order order = saveOrder(USER_ID, 5_000L);
        order.markPaid();

        CoreException ex = assertThrows(CoreException.class,
            () -> facade.requestPayment(USER_ID, commandFor(order.getId())));
        assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
    }

    @DisplayName("PG 응답 status 가 PENDING 외 다른 값이어도 transactionKey 만 받으면 IN_PROGRESS 로 전이된다.")
    @Test
    void requestPayment_handlesDifferentPgStatusGracefully() {
        Order order = saveOrder(USER_ID, 5_000L);
        gateway.setRequestBehavior(req -> new PgResponse("TX-CUSTOM", PgStatus.PENDING, null));

        PaymentInfo info = facade.requestPayment(USER_ID, commandFor(order.getId()));

        Payment payment = paymentRepository.find(info.paymentId()).orElseThrow();
        assertThat(payment.getTransactionKey()).isEqualTo("TX-CUSTOM");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
    }
}
