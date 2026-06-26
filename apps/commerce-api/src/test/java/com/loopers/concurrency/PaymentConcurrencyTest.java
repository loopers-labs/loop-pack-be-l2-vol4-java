package com.loopers.concurrency;

import com.loopers.application.payment.PaymentService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.user.Gender;
import com.loopers.domain.user.User;
import com.loopers.domain.vo.Money;
import com.loopers.domain.vo.Quantity;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.payment.PaymentJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 결제 접수 멱등 동시성 — 같은 주문에 결제 요청이 동시에(따닥) 와도, 비관적 락(주문 FOR UPDATE)이
 * createPending 을 직렬화해 활성 결제가 정확히 1건만 생성되어야 한다(이중 결제 방지).
 */
@SpringBootTest
class PaymentConcurrencyTest {

    private static final int ATTEMPTS = 10;

    private final PaymentService paymentService;
    private final UserJpaRepository userJpaRepository;
    private final OrderJpaRepository orderJpaRepository;
    private final PaymentJpaRepository paymentJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    private Long userId;
    private Long orderId;

    @Autowired
    PaymentConcurrencyTest(
        PaymentService paymentService,
        UserJpaRepository userJpaRepository,
        OrderJpaRepository orderJpaRepository,
        PaymentJpaRepository paymentJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.paymentService = paymentService;
        this.userJpaRepository = userJpaRepository;
        this.orderJpaRepository = orderJpaRepository;
        this.paymentJpaRepository = paymentJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        User user = userJpaRepository.save(new User(
            "tester01", "Password1!", "홍길동", "1990-05-14", "test@example.com", Gender.M));
        userId = user.getId();
        Order order = orderJpaRepository.save(new Order(
            userId, List.of(new OrderItem(10L, "에어맥스", Money.of(5000L), Quantity.of(1)))));
        orderId = order.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("같은 주문에 결제 접수가 10건 동시에 오면, 활성 결제는 정확히 1건만 생성된다. (이중 결제 방지)")
    @Test
    void createsExactlyOnePayment_underConcurrency() throws InterruptedException {
        // arrange
        AtomicInteger ok = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(ATTEMPTS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(ATTEMPTS);

        // act
        for (int i = 0; i < ATTEMPTS; i++) {
            executor.submit(() -> {
                try {
                    start.await(); // 동시 출발
                    paymentService.createPending(userId, orderId, CardType.SAMSUNG);
                    ok.incrementAndGet();
                } catch (Exception ignored) {
                    // 락 경합 실패 등은 무시 (핵심은 활성 결제 1건)
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        // assert — 모두 성공하되(기존 PENDING 반환 포함) 활성 결제는 1건만
        assertAll(
            () -> assertThat(ok.get()).isEqualTo(ATTEMPTS),
            () -> assertThat(paymentJpaRepository.count()).isEqualTo(1L)
        );
    }
}
