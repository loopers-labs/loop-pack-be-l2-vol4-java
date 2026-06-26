package com.loopers.application.payment;

import com.loopers.domain.order.OrderItemData;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgPaymentCommand;
import com.loopers.domain.payment.PgTransaction;
import com.loopers.domain.user.Gender;
import com.loopers.domain.user.PasswordEncryptor;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class PaymentReconciliationIntegrationTest {

    private static final String CARD_NO = "1234-5678-9814-1451";

    @TestConfiguration
    static class FakeGatewayConfig {
        @Bean
        @Primary
        ScriptableGateway scriptableGateway() {
            return new ScriptableGateway();
        }
    }

    @Autowired
    private PaymentFacade paymentFacade;
    @Autowired
    private ScriptableGateway gateway;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncryptor passwordEncryptor;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;
    @Autowired
    private RedisCleanUp redisCleanUp;

    @BeforeEach
    void resetGateway() {
        gateway.byKey = null;
        gateway.byOrder = new ArrayList<>();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private PaymentModel setupPending(String key, ZonedDateTime createdAt) {
        UserModel user = userRepository.save(new UserModel("user01", "Password1!", "홍길동", "1990-01-01",
                "user@example.com", Gender.MALE, passwordEncryptor));
        OrderModel order = orderRepository.save(OrderModel.create(user.getId(),
                List.of(new OrderItemData(1L, "상품", BigDecimal.valueOf(5000), 1L)), BigDecimal.ZERO));
        PaymentModel payment = PaymentModel.pending(
                order.getId(), order.getOrderNumber(), user.getId(), CardType.SAMSUNG, CARD_NO, 5000L);
        if (key != null) {
            payment.attachTransactionKey(key);
        }
        PaymentModel saved = paymentRepository.save(payment);
        if (createdAt != null) {
            ReflectionTestUtils.setField(saved, "createdAt", createdAt);
            saved = paymentRepository.save(saved);
        }
        return saved;
    }

    @DisplayName("PG 가 SUCCESS 를 주면 PAID 로 확정되고 주문도 PAID 가 된다.")
    @Test
    void confirmsPaid_whenGatewaySuccess() {
        // given
        PaymentModel payment = setupPending("20260626:TR:abc", null);
        gateway.byKey = new PgTransaction("20260626:TR:abc", PaymentStatus.PAID, "정상 승인", 5000L);

        // when
        ReconcileOutcome outcome = paymentFacade.reconcile(payment);

        // then
        assertAll(
                () -> assertThat(outcome).isEqualTo(ReconcileOutcome.PAID),
                () -> assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
                        .isEqualTo(PaymentStatus.PAID),
                () -> assertThat(orderRepository.findByOrderNumber(payment.getOrderNumber()).orElseThrow().getStatus())
                        .isEqualTo(OrderStatus.PAID)
        );
    }

    @DisplayName("PG 가 FAILED 를 주면 FAILED 로 확정되고 주문은 PAYMENT_FAILED 가 된다.")
    @Test
    void confirmsFailed_whenGatewayFailed() {
        // given
        PaymentModel payment = setupPending("20260626:TR:abc", null);
        gateway.byKey = new PgTransaction("20260626:TR:abc", PaymentStatus.FAILED, "한도초과", 5000L);

        // when
        ReconcileOutcome outcome = paymentFacade.reconcile(payment);

        // then
        assertAll(
                () -> assertThat(outcome).isEqualTo(ReconcileOutcome.FAILED),
                () -> assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
                        .isEqualTo(PaymentStatus.FAILED),
                () -> assertThat(orderRepository.findByOrderNumber(payment.getOrderNumber()).orElseThrow().getStatus())
                        .isEqualTo(OrderStatus.PAYMENT_FAILED)
        );
    }

    @DisplayName("PG 가 처리 중(PENDING)이면 건드리지 않고 다음 주기 재확인 상태로 둔다.")
    @Test
    void leavesPending_whenStillProcessing() {
        // given
        PaymentModel payment = setupPending("20260626:TR:abc", null);
        gateway.byKey = new PgTransaction("20260626:TR:abc", PaymentStatus.PENDING, null, null);

        // when
        ReconcileOutcome outcome = paymentFacade.reconcile(payment);

        // then
        assertAll(
                () -> assertThat(outcome).isEqualTo(ReconcileOutcome.STILL_PROCESSING),
                () -> assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
                        .isEqualTo(PaymentStatus.PENDING)
        );
    }

    @DisplayName("주문 없음(미도달)이면 FAILED 로 확정한다(자동 재요청 X).")
    @Test
    void confirmsFailed_whenUnreached() {
        // given — transactionKey 없이 orderNumber 로 되짚지만 PG 에 거래가 없음
        PaymentModel payment = setupPending(null, null);
        gateway.byOrder = new ArrayList<>(); // 빈 결과 = 주문 없음

        // when
        ReconcileOutcome outcome = paymentFacade.reconcile(payment);

        // then
        assertAll(
                () -> assertThat(outcome).isEqualTo(ReconcileOutcome.UNREACHED_FAILED),
                () -> assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
                        .isEqualTo(PaymentStatus.FAILED)
        );
    }

    @DisplayName("PG 도 여전히 처리 중이면서 createdAt 이 10분을 초과하면 UNKNOWN 으로 격리한다(상한 초과).")
    @Test
    void isolatesToUnknown_whenStillProcessingAndGraceExceeded() {
        // given — PG 가 PENDING(처리 중)을 주고, 결제는 11분 전 생성됨
        PaymentModel payment = setupPending("20260626:TR:abc", ZonedDateTime.now().minusMinutes(11));
        gateway.byKey = new PgTransaction("20260626:TR:abc", PaymentStatus.PENDING, null, null);

        // when
        ReconcileOutcome outcome = paymentFacade.reconcile(payment);

        // then
        assertAll(
                () -> assertThat(outcome).isEqualTo(ReconcileOutcome.ISOLATED),
                () -> assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
                        .isEqualTo(PaymentStatus.UNKNOWN)
        );
    }

    @DisplayName("createdAt 이 10분을 초과해도 PG 가 SUCCESS 를 주면 PAID 로 확정한다(상한보다 PG 결과 우선).")
    @Test
    void confirmsPaid_whenGraceExceededButGatewaySuccess() {
        // given — 11분 전 생성됐지만 PG 는 SUCCESS
        PaymentModel payment = setupPending("20260626:TR:abc", ZonedDateTime.now().minusMinutes(11));
        gateway.byKey = new PgTransaction("20260626:TR:abc", PaymentStatus.PAID, "정상 승인", 5000L);

        // when
        ReconcileOutcome outcome = paymentFacade.reconcile(payment);

        // then
        assertAll(
                () -> assertThat(outcome).isEqualTo(ReconcileOutcome.PAID),
                () -> assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
                        .isEqualTo(PaymentStatus.PAID),
                () -> assertThat(orderRepository.findByOrderNumber(payment.getOrderNumber()).orElseThrow().getStatus())
                        .isEqualTo(OrderStatus.PAID)
        );
    }

    /** 응답을 필드로 주입할 수 있는 PaymentGateway Fake. */
    static class ScriptableGateway implements PaymentGateway {
        PgTransaction byKey;
        List<PgTransaction> byOrder = new ArrayList<>();

        @Override
        public PgTransaction request(PgPaymentCommand command) {
            return new PgTransaction("20260626:TR:default", PaymentStatus.PENDING, null, null);
        }

        @Override
        public Optional<PgTransaction> findByTransactionKey(String transactionKey) {
            return Optional.ofNullable(byKey);
        }

        @Override
        public List<PgTransaction> findByOrderId(String orderNumber) {
            return byOrder;
        }
    }
}
