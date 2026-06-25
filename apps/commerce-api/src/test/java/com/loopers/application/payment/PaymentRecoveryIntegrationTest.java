package com.loopers.application.payment;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.order.OrderCommand;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.user.UserCommand;
import com.loopers.application.user.UserFacade;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayTransaction;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.payment.PaymentJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * key 를 못 받은 채 멈춘 결제(keyless)를 orderId 로 PG 에 되물어 수렴시키는 복구(by order) 통합 검증.
 * setUp 이 PG 접수 실패를 주입해 fallback 으로 keyless PENDING 결제를 만들고, 그 위에서 by-order 복구를 돌린다.
 * pending-threshold 를 0 으로 낮춰 방금 생성한 결제도 즉시 복구 대상이 되게 한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
    "payment-recovery.pending-threshold=0s",
    "payment-recovery.scan-interval=1h"
})
class PaymentRecoveryIntegrationTest {

    private final PaymentFacade paymentFacade;
    private final PaymentRecovery paymentRecovery;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final BrandFacade brandFacade;
    private final ProductFacade productFacade;
    private final UserFacade userFacade;
    private final OrderFacade orderFacade;
    private final OrderJpaRepository orderJpaRepository;
    private final PaymentJpaRepository paymentJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @MockitoBean
    private PaymentGateway paymentGateway;

    private Long userId;
    private Long orderId;
    private Long paymentId;

    @Autowired
    public PaymentRecoveryIntegrationTest(
        PaymentFacade paymentFacade,
        PaymentRecovery paymentRecovery,
        CircuitBreakerRegistry circuitBreakerRegistry,
        BrandFacade brandFacade,
        ProductFacade productFacade,
        UserFacade userFacade,
        OrderFacade orderFacade,
        OrderJpaRepository orderJpaRepository,
        PaymentJpaRepository paymentJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.paymentFacade = paymentFacade;
        this.paymentRecovery = paymentRecovery;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.brandFacade = brandFacade;
        this.productFacade = productFacade;
        this.userFacade = userFacade;
        this.orderFacade = orderFacade;
        this.orderJpaRepository = orderJpaRepository;
        this.paymentJpaRepository = paymentJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        circuitBreakerRegistry.circuitBreaker("pg").reset();

        // PG 동기 접수를 실패시켜 fallback 으로 keyless PENDING 결제를 만든다
        given(paymentGateway.requestPayment(any())).willThrow(new ResourceAccessException("PG 응답 없음"));

        Long brandId = brandFacade.create("나이키", "Just Do It").id();
        Long productId = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 100_000L, 10, brandId).id();
        userId = userFacade.signUp(new UserCommand.SignUp(
            "user01", "Abcd1234!", "김철수", LocalDate.of(1999, 3, 22), "user@example.com"
        )).id();
        OrderInfo order = orderFacade.placeOrder(userId, new OrderCommand.Place(List.of(
            new OrderCommand.Line(productId, 2)
        )));
        orderId = order.id();

        PaymentInfo info = paymentFacade.pay(userId, new PaymentCommand.Pay(orderId, "SAMSUNG", "1234-5678-9814-1451"));
        paymentId = info.paymentId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("key 없이 멈춘 결제를 orderId 로 되물어 복구할 때, ")
    @Nested
    class RecoverStuckByOrder {

        @DisplayName("PG 가 빈 목록(미접수)을 주면, 결제를 FAILED 로 정정하고 주문을 결제실패로 전이한다.")
        @Test
        void marksFailed_whenPgHasNoTransaction() {
            // given
            given(paymentGateway.getTransactionsByOrder(userId, orderId)).willReturn(List.of());

            // when
            paymentRecovery.recoverStuckByOrder();

            // then
            PaymentModel payment = paymentJpaRepository.findById(paymentId).orElseThrow();
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                () -> assertThat(payment.getTransactionKey()).isNull(),
                () -> assertThat(orderJpaRepository.findById(orderId).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.PAYMENT_FAILED)
            );
        }

        @DisplayName("PG 가 SUCCESS 트랜잭션을 주면, transactionKey 를 흡수해 결제를 성공 처리하고 주문을 결제완료로 전이한다.")
        @Test
        void marksSuccessAbsorbingKey_whenPgReportsSuccess() {
            // given
            given(paymentGateway.getTransactionsByOrder(userId, orderId)).willReturn(List.of(
                new PaymentGatewayTransaction("20260625:TR:recovered", PaymentStatus.SUCCESS, "정상 승인되었습니다.")));

            // when
            paymentRecovery.recoverStuckByOrder();

            // then
            PaymentModel payment = paymentJpaRepository.findById(paymentId).orElseThrow();
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS),
                () -> assertThat(payment.getTransactionKey()).isEqualTo("20260625:TR:recovered"),
                () -> assertThat(orderJpaRepository.findById(orderId).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.PAID)
            );
        }

        @DisplayName("PG 가 아직 PENDING 트랜잭션을 주면, transactionKey 만 흡수하고 PENDING 을 유지해 다음 스캔이 key 로 수렴하게 한다.")
        @Test
        void absorbsKeyButStaysPending_whenPgStillPending() {
            // given
            given(paymentGateway.getTransactionsByOrder(userId, orderId)).willReturn(List.of(
                new PaymentGatewayTransaction("20260625:TR:inflight", PaymentStatus.PENDING, null)));

            // when
            paymentRecovery.recoverStuckByOrder();

            // then
            PaymentModel payment = paymentJpaRepository.findById(paymentId).orElseThrow();
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(payment.getTransactionKey()).isEqualTo("20260625:TR:inflight"),
                () -> assertThat(orderJpaRepository.findById(orderId).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.PAYMENT_PENDING)
            );
        }
    }
}
