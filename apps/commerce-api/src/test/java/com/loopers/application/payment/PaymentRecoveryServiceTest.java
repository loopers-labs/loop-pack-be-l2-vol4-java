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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentRecoveryServiceTest {

    private static final String CARD = "1234-5678-9012-3456";

    private FakePgClient fakePgClient;
    private PaymentService paymentService;
    private OrderService orderService;
    private PaymentFacade facade;
    private PaymentRecoveryService recovery;

    @BeforeEach
    void setUp() {
        fakePgClient = new FakePgClient();
        FakePaymentRepository paymentRepo = new FakePaymentRepository();
        FakeOrderRepository orderRepo = new FakeOrderRepository();
        paymentService = new PaymentService(paymentRepo);
        orderService = new OrderService(orderRepo);
        facade = new PaymentFacade(orderService, paymentService, fakePgClient);
        recovery = new PaymentRecoveryService(paymentService, facade, fakePgClient);
        ReflectionTestUtils.setField(facade, "callbackUrl", "http://localhost/callback");
        ReflectionTestUtils.setField(recovery, "requestedStaleAfter", Duration.ofSeconds(30));
        ReflectionTestUtils.setField(recovery, "batchSize", 50);
    }

    private OrderModel newOrder(long userId, long price) {
        OrderModel order = new OrderModel(userId, List.of(
            new OrderItemModel(1L, "상품", "브랜드", price, null, 1)
        ));
        return orderService.save(order);
    }

    @DisplayName("TIMEOUT_PENDING 결제 복구")
    @Nested
    class TimeoutRecovery {

        @DisplayName("PG 측이 이미 SUCCESS 로 처리된 결제는, 다음 폴링에서 SUCCEEDED 로 동기화된다.")
        @Test
        void timeoutSyncsToSuccess() {
            OrderModel order = newOrder(100L, 5000L);

            // PG 호출은 정상이지만, 응답 받기 전에 우리쪽 타임아웃 가정
            fakePgClient.scenario = FakePgClient.Scenario.TIMEOUT;
            facade.request(new PaymentCriteria.Request(100L, order.getId(), "SAMSUNG", CARD));

            // 실제로 PG 측에서는 처리가 완료되어 SUCCESS 가 된 상태
            String pgKey = "TX-orphan";
            fakePgClient.byKey.put(pgKey,
                new com.loopers.domain.payment.PgClient.PgTransactionView(pgKey, order.getId(), PgTransactionStatus.SUCCESS, null));
            fakePgClient.byOrder.put(order.getId(),
                new java.util.ArrayList<>(List.of(fakePgClient.byKey.get(pgKey))));

            // 복구 실행
            recovery.recoverRecoverablePayments();

            PaymentModel recovered = paymentService.getByOrderId(order.getId());
            assertThat(recovered.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
            assertThat(recovered.getTransactionKey()).isEqualTo(pgKey);
        }

        @DisplayName("PG 가 아직 PENDING 이면, 결제 상태는 그대로 두고 다음 주기에 재시도한다.")
        @Test
        void stillPendingStaysOnPending() {
            OrderModel order = newOrder(100L, 5000L);
            fakePgClient.scenario = FakePgClient.Scenario.TIMEOUT;
            facade.request(new PaymentCriteria.Request(100L, order.getId(), "SAMSUNG", CARD));

            // PG 측은 아직 PENDING — orderId 로 검색되지만 종료 상태 아님
            String pgKey = "TX-pending";
            fakePgClient.byKey.put(pgKey,
                new com.loopers.domain.payment.PgClient.PgTransactionView(pgKey, order.getId(), PgTransactionStatus.PENDING, null));
            fakePgClient.byOrder.put(order.getId(),
                new java.util.ArrayList<>(List.of(fakePgClient.byKey.get(pgKey))));

            recovery.recoverRecoverablePayments();

            PaymentModel after = paymentService.getByOrderId(order.getId());
            assertThat(after.getStatus()).isEqualTo(PaymentStatus.TIMEOUT_PENDING);
            // transactionKey 는 attach 되어야 함 (다음 주기에 단건 GET 가능하도록)
            assertThat(after.getTransactionKey()).isEqualTo(pgKey);
        }
    }
}
