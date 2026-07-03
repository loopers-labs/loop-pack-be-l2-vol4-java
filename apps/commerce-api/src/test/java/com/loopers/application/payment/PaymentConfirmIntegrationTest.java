package com.loopers.application.payment;

import com.loopers.domain.order.OrderItemData;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.ConfirmOutcome;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.user.Gender;
import com.loopers.domain.user.PasswordEncryptor;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
class PaymentConfirmIntegrationTest {

    private static final String CARD_NO = "1234-5678-9814-1451";

    @Autowired
    private PaymentFacade paymentFacade;
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
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private PaymentModel setupPending(String key) {
        UserModel user = userRepository.save(new UserModel("user01", "Password1!", "홍길동", "1990-01-01",
                "user@example.com", Gender.MALE, passwordEncryptor));
        OrderModel order = orderRepository.save(OrderModel.create(user.getId(),
                List.of(new OrderItemData(1L, "상품", BigDecimal.valueOf(5000), 1L)), BigDecimal.ZERO));
        PaymentModel payment = PaymentModel.pending(
                order.getId(), order.getOrderNumber(), user.getId(), CardType.SAMSUNG, CARD_NO, 5000L);
        payment.attachTransactionKey(key);
        return paymentRepository.save(payment);
    }

    @DisplayName("무결성 가드를 적용할 때,")
    @Nested
    class IntegrityGuard {

        @DisplayName("amount 불일치 콜백은 전이를 거부하고 UNKNOWN 으로 격리한다(주문 미확정).")
        @Test
        void isolatesToUnknown_whenAmountMismatch() {
            // given
            PaymentModel payment = setupPending("20260626:TR:abc");

            // when
            paymentFacade.confirmResult("20260626:TR:abc", "SUCCESS", "정상 승인", 9999L, CARD_NO);

            // then
            PaymentModel reloaded = paymentRepository.findById(payment.getId()).orElseThrow();
            OrderModel order = orderRepository.findByOrderNumber(payment.getOrderNumber()).orElseThrow();
            assertAll(
                    () -> assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.UNKNOWN),
                    () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PLACED)
            );
        }

        @DisplayName("cardNo 불일치 콜백은 전이를 거부하고 UNKNOWN 으로 격리한다.")
        @Test
        void isolatesToUnknown_whenCardNoMismatch() {
            // given
            PaymentModel payment = setupPending("20260626:TR:abc");

            // when
            paymentFacade.confirmResult("20260626:TR:abc", "SUCCESS", "정상 승인", 5000L, "9999-8888-7777-6666");

            // then
            assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
                    .isEqualTo(PaymentStatus.UNKNOWN);
        }
    }

    @DisplayName("콜백과 폴링이 동시에 같은 결제를 확정할 때,")
    @Nested
    class Concurrency {

        @DisplayName("조건부 UPDATE 로 후처리(PAID 확정)는 정확히 한 번만 일어난다.")
        @Test
        void postProcessesExactlyOnce_whenCallbackAndPollingRace() throws Exception {
            // given
            PaymentModel payment = setupPending("20260626:TR:abc");
            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);

            // when — 두 스레드가 동시에 동일 결제를 confirm
            Future<ConfirmOutcome> f1 = executor.submit(() -> {
                start.await();
                return paymentFacade.confirmResult("20260626:TR:abc", "SUCCESS", "정상 승인", 5000L, CARD_NO);
            });
            Future<ConfirmOutcome> f2 = executor.submit(() -> {
                start.await();
                return paymentFacade.confirmResult("20260626:TR:abc", "SUCCESS", "정상 승인", 5000L, CARD_NO);
            });
            start.countDown();

            ConfirmOutcome o1 = assertDoesNotThrow(() -> f1.get());
            ConfirmOutcome o2 = assertDoesNotThrow(() -> f2.get());
            executor.shutdown();

            // then — 정확히 한 쪽만 PAID(후처리 트리거), 다른 쪽은 SKIPPED(멱등)
            long paidCount = List.of(o1, o2).stream().filter(o -> o.result() == ConfirmOutcome.Result.PAID).count();
            long skippedCount = List.of(o1, o2).stream().filter(o -> o.result() == ConfirmOutcome.Result.SKIPPED).count();
            PaymentModel reloaded = paymentRepository.findById(payment.getId()).orElseThrow();
            OrderModel order = orderRepository.findByOrderNumber(payment.getOrderNumber()).orElseThrow();
            assertAll(
                    () -> assertThat(paidCount).isEqualTo(1),
                    () -> assertThat(skippedCount).isEqualTo(1),
                    () -> assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.PAID),
                    () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID)
            );
        }
    }
}
