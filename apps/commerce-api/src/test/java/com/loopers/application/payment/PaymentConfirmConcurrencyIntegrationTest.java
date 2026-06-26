package com.loopers.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.ZonedDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PaymentTransactionStatus;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.payment.PaymentJpaRepository;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest
class PaymentConfirmConcurrencyIntegrationTest {

    private static final String CARD_NO = "1234-5678-9012-3456";
    private static final String TX_KEY = "TX-0001";

    @Autowired
    private PaymentTransactionWriter paymentTransactionWriter;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private PaymentJpaRepository paymentJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("콜백 스레드와 폴링 스레드가 같은 결제를 동시에 확정해도 후처리는 정확히 한 번만 일어난다.")
    @Test
    void confirmsExactlyOnce_underConcurrentConfirmation() throws InterruptedException {
        // arrange
        OrderModel order = orderJpaRepository.save(OrderModel.builder()
            .userId(1L)
            .orderedAt(ZonedDateTime.now())
            .originalAmount(78_000)
            .discountAmount(0)
            .finalAmount(78_000)
            .build());
        PaymentModel payment = PaymentModel.builder()
            .orderId(order.getId())
            .userId(1L)
            .amount(78_000)
            .cardType(CardType.SAMSUNG)
            .rawCardNo(CARD_NO)
            .requestedAt(ZonedDateTime.now())
            .build();
        payment.recordTransactionKey(TX_KEY);
        PaymentModel savedPayment = paymentJpaRepository.save(payment);

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger winnerCount = new AtomicInteger();

        // act
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    boolean won = paymentTransactionWriter.confirm(
                        savedPayment, PaymentTransactionStatus.found(TX_KEY, PaymentStatus.SUCCESS, null));
                    if (won) {
                        winnerCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        ready.await();
        start.countDown();
        executor.shutdown();
        boolean terminated = executor.awaitTermination(10, TimeUnit.SECONDS);

        // assert
        assertAll(
            () -> assertThat(terminated).isTrue(),
            () -> assertThat(winnerCount.get()).isEqualTo(1),
            () -> assertThat(paymentJpaRepository.findById(savedPayment.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.SUCCESS),
            () -> assertThat(orderJpaRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID)
        );
    }
}
