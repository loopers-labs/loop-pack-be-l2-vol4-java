package com.loopers.application.payment;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderItems;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentGateway;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 결제 요청 동시성(따닥) — {@link PaymentReserver} 의 비관락 예약이 같은 주문의 동시 결제를 직렬화하는지 검증한다.
 *
 * <p>핵심: 패자 트랜잭션은 {@code findForUpdate}(FOR UPDATE)에서 승자 커밋까지 블록되므로, 이어지는 중복
 * 검사 스냅샷이 승자의 PENDING 을 보고 CONFLICT 로 떨어진다 → 정확히 1건만 PENDING, PG 호출도 1회.</p>
 *
 * <p>실제 pg-simulator 대신 호출 횟수를 세는 {@link CountingPgGateway} 스텁을 {@code @Primary} 로 주입한다.</p>
 */
@DisplayName("결제 요청 동시성(따닥) — 비관락 예약")
@SpringBootTest
class PaymentConcurrencyIntegrationTest {

    private static final Long USER_ID = 7L;

    @Autowired
    private PaymentApplicationService paymentApplicationService;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private PaymentJpaRepository paymentJpaRepository;

    @Autowired
    private CountingPgGateway pgGateway;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long orderId;

    @BeforeEach
    void setUp() {
        Order order = Order.create(
                USER_ID,
                OrderItems.from(List.of(OrderItem.of(1L, "상품", Money.of(5_000L), 1))),
                Money.of(0L));
        orderId = orderJpaRepository.save(order).getId();
        pgGateway.reset();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("같은 주문에 동시 결제요청 N건이 들어와도 정확히 1건만 성공(PENDING)하고 나머지는 CONFLICT 이며, PG 는 1회만 호출된다.")
    @Test
    void concurrentPay_onlyOneSucceeds_pgCalledOnce() throws InterruptedException {
        int attempts = 20;
        ExecutorService executor = Executors.newFixedThreadPool(16);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(attempts);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();

        for (int i = 0; i < attempts; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    paymentApplicationService.pay(new PaymentCriteria.Pay(
                            USER_ID, orderId, CardType.SAMSUNG, "1234-5678-9814-1451"));
                    success.incrementAndGet();
                } catch (CoreException e) {
                    if (e.getErrorType() == ErrorType.CONFLICT) {
                        conflict.incrementAndGet();
                    } else {
                        other.incrementAndGet();
                    }
                } catch (Exception e) {
                    other.incrementAndGet();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean finished = doneGate.await(30, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(finished).as("모든 시도가 30초 내에 끝나야 한다").isTrue();
        assertThat(success.get()).as("성공(PENDING) 건수").isEqualTo(1);
        assertThat(conflict.get()).as("CONFLICT 건수").isEqualTo(attempts - 1);
        assertThat(other.get()).as("그 외 예외(0이어야 함)").isZero();

        List<Payment> payments = paymentJpaRepository.findAllByOrderId(orderId);
        assertThat(payments).as("주문에 대한 결제 행은 1건뿐이어야 한다").hasSize(1);
        assertThat(pgGateway.requestCount()).as("PG 결제요청 호출 횟수").isEqualTo(1);
    }

    @TestConfiguration
    static class StubConfig {

        @Bean
        @Primary
        CountingPgGateway countingPgGateway() {
            return new CountingPgGateway();
        }
    }

    /** 호출 횟수를 세고 매번 고유 transactionKey 를 돌려주는 PG 스텁(실제 pg-simulator 미사용). */
    static class CountingPgGateway implements PaymentGateway {

        private final AtomicInteger count = new AtomicInteger();

        void reset() {
            count.set(0);
        }

        int requestCount() {
            return count.get();
        }

        @Override
        public Result request(Command command) {
            int n = count.incrementAndGet();
            return new Result("TR-TEST-" + n, PaymentStatus.PENDING, null);
        }

        @Override
        public Optional<Result> find(String transactionKey, String userId) {
            return Optional.empty();
        }
    }
}
