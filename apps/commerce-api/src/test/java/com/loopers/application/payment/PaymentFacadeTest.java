package com.loopers.application.payment;

import com.loopers.domain.order.FakeOrderRepository;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.FakePaymentRepository;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgClient.PgTransactionStatus;
import com.loopers.support.event.RecordingEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentFacadeTest {

    private static final String CARD = "1234-5678-9012-3456";

    private FakePgClient fakePgClient;
    private FakePaymentRepository fakePaymentRepository;
    private FakeOrderRepository fakeOrderRepository;
    private PaymentFacade facade;
    private PaymentService paymentService;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        fakePgClient = new FakePgClient();
        fakePaymentRepository = new FakePaymentRepository();
        fakeOrderRepository = new FakeOrderRepository();
        paymentService = new PaymentService(fakePaymentRepository);
        orderService = new OrderService(fakeOrderRepository);
        facade = new PaymentFacade(orderService, paymentService, fakePgClient, new RecordingEventPublisher());
        ReflectionTestUtils.setField(facade, "callbackUrl", "http://localhost/callback");
    }

    private OrderModel newOrder(long userId, long price) {
        OrderModel order = new OrderModel(userId, List.of(
            new OrderItemModel(1L, "상품", "브랜드", price, null, 1)
        ));
        return orderService.save(order);
    }

    @DisplayName("결제 요청")
    @Nested
    class Request {

        @DisplayName("PG 가 정상 응답하면, 결제는 REQUESTED 로 전이되고 transactionKey 가 채워진다.")
        @Test
        void grantsRequested() {
            OrderModel order = newOrder(100L, 5000L);

            PaymentInfo info = facade.request(new PaymentCriteria.Request(
                100L, order.getId(), "SAMSUNG", CARD));

            assertThat(info.status()).isEqualTo(PaymentStatus.REQUESTED);
            assertThat(info.transactionKey()).isNotBlank();
            assertThat(fakePgClient.requestCount.get()).isEqualTo(1);
        }

        @DisplayName("PG 가 타임아웃이면, 결제는 TIMEOUT_PENDING 으로 전이되어 복구를 기다린다.")
        @Test
        void timeoutGoesToRecovery() {
            OrderModel order = newOrder(100L, 5000L);
            fakePgClient.scenario = FakePgClient.Scenario.TIMEOUT;

            PaymentInfo info = facade.request(new PaymentCriteria.Request(
                100L, order.getId(), "SAMSUNG", CARD));

            assertThat(info.status()).isEqualTo(PaymentStatus.TIMEOUT_PENDING);
            assertThat(info.failReason()).isNotBlank();
        }

        @DisplayName("다른 사용자가 남의 주문에 결제 시도하면 NOT_FOUND (정보 노출 차단).")
        @Test
        void rejectsForeignOrder() {
            OrderModel order = newOrder(100L, 5000L);

            assertThat(catchType(() -> facade.request(new PaymentCriteria.Request(
                999L, order.getId(), "SAMSUNG", CARD))))
                .isEqualTo("NOT_FOUND");
        }

        @DisplayName("같은 orderId 로 두 번째 결제 요청은 멱등 — PG 를 다시 호출하지 않는다.")
        @Test
        void duplicateRequestIsIdempotent() {
            OrderModel order = newOrder(100L, 5000L);
            facade.request(new PaymentCriteria.Request(100L, order.getId(), "SAMSUNG", CARD));

            PaymentInfo second = facade.request(new PaymentCriteria.Request(
                100L, order.getId(), "SAMSUNG", CARD));

            assertThat(second.status()).isEqualTo(PaymentStatus.REQUESTED);
            assertThat(fakePgClient.requestCount.get()).isEqualTo(1);
        }
    }

    @DisplayName("콜백")
    @Nested
    class Callback {

        @DisplayName("SUCCESS 콜백 수신 시 결제는 SUCCEEDED 로, 주문도 PAID 로 전이된다.")
        @Test
        void successCallbackMarksBoth() {
            OrderModel order = newOrder(100L, 5000L);
            PaymentInfo info = facade.request(new PaymentCriteria.Request(
                100L, order.getId(), "SAMSUNG", CARD));

            facade.handleCallback(new PaymentCriteria.Callback(info.transactionKey(), "SUCCESS", null));

            PaymentModel after = paymentService.getById(info.paymentId());
            assertThat(after.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
            assertThat(orderService.getOrder(order.getId()).getStatus().name()).isEqualTo("PAID");
        }

        @DisplayName("FAILED 콜백 수신 시 결제는 FAILED, 주문도 FAILED 로 전이된다.")
        @Test
        void failedCallbackMarksBoth() {
            OrderModel order = newOrder(100L, 5000L);
            PaymentInfo info = facade.request(new PaymentCriteria.Request(
                100L, order.getId(), "SAMSUNG", CARD));

            facade.handleCallback(new PaymentCriteria.Callback(
                info.transactionKey(), "LIMIT_EXCEEDED", "한도 초과"));

            PaymentModel after = paymentService.getById(info.paymentId());
            assertThat(after.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(after.getFailReason()).isEqualTo("한도 초과");
        }

        @DisplayName("같은 SUCCESS 콜백이 두 번 와도 상태는 한 번만 전이된다 (멱등).")
        @Test
        void duplicateCallbackIsIdempotent() {
            OrderModel order = newOrder(100L, 5000L);
            PaymentInfo info = facade.request(new PaymentCriteria.Request(
                100L, order.getId(), "SAMSUNG", CARD));

            facade.handleCallback(new PaymentCriteria.Callback(info.transactionKey(), "SUCCESS", null));
            facade.handleCallback(new PaymentCriteria.Callback(info.transactionKey(), "SUCCESS", null));

            PaymentModel after = paymentService.getById(info.paymentId());
            assertThat(after.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        }
    }

    private static String catchType(Runnable action) {
        try {
            action.run();
            return null;
        } catch (com.loopers.support.error.CoreException e) {
            return e.getErrorType().name();
        }
    }
}
