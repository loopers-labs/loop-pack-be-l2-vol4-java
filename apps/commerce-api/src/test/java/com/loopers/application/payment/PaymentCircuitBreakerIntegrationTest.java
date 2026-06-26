package com.loopers.application.payment;

import com.loopers.domain.order.OrderItemData;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.user.Gender;
import com.loopers.domain.user.PasswordEncryptor;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * Fallback(Must-Have) 검증: CircuitBreaker 가 OPEN 이면 PG 를 호출조차 하지 않고(CallNotPermittedException),
 * facade 가 이를 PENDING "처리 중"으로 흡수한다(설계 §7.3). Fake 게이트웨이 없이 실제 어댑터(CB 적용) 경로를 탄다.
 */
@SpringBootTest
class PaymentCircuitBreakerIntegrationTest {

    private static final String LOGIN_ID = "user01";
    private static final String LOGIN_PW = "Password1!";
    private static final String CARD_NO = "1234-5678-9814-1451";

    @Autowired
    private PaymentFacade paymentFacade;
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;
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

    @AfterEach
    void tearDown() {
        circuitBreakerRegistry.circuitBreaker("paymentRequest").transitionToClosedState();
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("CircuitBreaker 가 OPEN 이면 PG 를 호출하지 않고 결제가 PENDING 으로 남는다.")
    @Test
    void leavesPending_whenCircuitBreakerOpen() {
        // given
        UserModel user = userRepository.save(new UserModel(LOGIN_ID, LOGIN_PW, "홍길동", "1990-01-01",
                "user@example.com", Gender.MALE, passwordEncryptor));
        OrderModel order = orderRepository.save(OrderModel.create(user.getId(),
                List.of(new OrderItemData(1L, "상품", BigDecimal.valueOf(5000), 1L)), BigDecimal.ZERO));
        circuitBreakerRegistry.circuitBreaker("paymentRequest").transitionToOpenState();

        // when
        PaymentInfo info = paymentFacade.pay(LOGIN_ID, LOGIN_PW, order.getOrderNumber(), CardType.SAMSUNG, CARD_NO);

        // then
        PaymentModel saved = paymentRepository.findById(info.paymentId()).orElseThrow();
        assertAll(
                () -> assertThat(info.status()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(saved.getTransactionKey()).isNull()
        );
    }
}
