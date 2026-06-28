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
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.payment.PaymentJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 동기 PG 접수 호출의 Resilience 검증. 외부 PG 는 stub 으로 실패를 주입한다.
 * 서킷은 작은 윈도(3건)로 오버라이드해 결정적으로 열리게 한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
    "resilience4j.circuitbreaker.instances.pg.sliding-window-size=3",
    "resilience4j.circuitbreaker.instances.pg.minimum-number-of-calls=3",
    "resilience4j.circuitbreaker.instances.pg.wait-duration-in-open-state=60s",
    "payment-recovery.scan-interval=1h"
})
class PaymentResilienceIntegrationTest {

    private final PaymentFacade paymentFacade;
    private final PaymentRequester paymentRequester;
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

    @Autowired
    public PaymentResilienceIntegrationTest(
        PaymentFacade paymentFacade,
        PaymentRequester paymentRequester,
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
        this.paymentRequester = paymentRequester;
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

        // PG 동기 접수는 항상 실패(transient)로 주입
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
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("PG 접수가 실패하면, ")
    @Nested
    class Fallback {

        @DisplayName("예외를 사용자에게 던지지 않고, 결제 진행중(PENDING·키없음)으로 강등해 응답한다.")
        @Test
        void degradesToPending_insteadOfThrowing() {
            // when
            PaymentInfo info = paymentFacade.pay(userId, new PaymentCommand.Pay(orderId, "SAMSUNG", "1234-5678-9814-1451"));

            // then
            assertAll(
                () -> assertThat(info.status()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(info.transactionKey()).isNull()
            );
        }

        @DisplayName("강등돼도 결제는 PENDING, 주문은 PAYMENT_PENDING 으로 영속돼 이후 복구가 수렴할 수 있다.")
        @Test
        void leavesPaymentAndOrderPending() {
            // when
            PaymentInfo info = paymentFacade.pay(userId, new PaymentCommand.Pay(orderId, "SAMSUNG", "1234-5678-9814-1451"));

            // then
            assertAll(
                () -> assertThat(paymentJpaRepository.findById(info.paymentId()).orElseThrow().getStatus())
                    .isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(paymentJpaRepository.findById(info.paymentId()).orElseThrow().getTransactionKey())
                    .isNull(),
                () -> assertThat(orderJpaRepository.findById(orderId).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.PAYMENT_PENDING)
            );
        }
    }

    @DisplayName("PG 실패가 누적되면, ")
    @Nested
    class CircuitOpen {

        @DisplayName("임계(3건 100% 실패)를 넘기면 서킷이 OPEN 되고, 이후 호출은 PG 를 때리지 않고 fail-fast 강등한다.")
        @Test
        void opensAndShortCircuits() {
            // given
            CircuitBreaker pg = circuitBreakerRegistry.circuitBreaker("pg");
            PaymentCommand.Pay command = new PaymentCommand.Pay(100L, "SAMSUNG", "1234-5678-9814-1451");
            PaymentInitiator.Initiated initiated = new PaymentInitiator.Initiated(999L, 50_000L);

            // when : 실패 3건으로 서킷을 연다
            for (int i = 0; i < 3; i++) {
                paymentRequester.requestAndAssign(userId, command, initiated);
            }

            // then : OPEN 전이 + 추가 호출은 PG 미접촉(short-circuit)
            assertThat(pg.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            paymentRequester.requestAndAssign(userId, command, initiated);
            verify(paymentGateway, times(3)).requestPayment(any());
        }
    }
}
