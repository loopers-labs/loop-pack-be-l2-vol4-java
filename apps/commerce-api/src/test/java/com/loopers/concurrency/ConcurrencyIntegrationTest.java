package com.loopers.concurrency;

import com.loopers.coupon.domain.CouponModel;
import com.loopers.coupon.domain.CouponStatus;
import com.loopers.coupon.domain.CouponType;
import com.loopers.coupon.infrastructure.CouponIssueJpaRepository;
import com.loopers.coupon.infrastructure.CouponJpaRepository;
import com.loopers.coupon.domain.CouponIssueModel;
import com.loopers.like.application.LikeFacade;
import com.loopers.order.application.OrderFacade;
import com.loopers.order.application.OrderItemCommand;
import com.loopers.product.domain.ProductModel;
import com.loopers.product.infrastructure.ProductJpaRepository;
import com.loopers.stock.domain.StockModel;
import com.loopers.stock.infrastructure.StockJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ConcurrencyIntegrationTest {

    @Autowired private LikeFacade likeFacade;
    @Autowired private OrderFacade orderFacade;
    @Autowired private ProductJpaRepository productJpaRepository;
    @Autowired private StockJpaRepository stockJpaRepository;
    @Autowired private CouponJpaRepository couponJpaRepository;
    @Autowired private CouponIssueJpaRepository couponIssueJpaRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("서로 다른 유저 10명이 동일 상품에 동시에 좋아요를 요청하면, likeCount가 10이 된다.")
    @Test
    void likeCount_isConsistent_whenMultipleUsersConcurrentlyLike() throws InterruptedException {
        // arrange
        ProductModel product = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, null));
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // act
        for (int i = 1; i <= threadCount; i++) {
            final long userId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    likeFacade.addLike(userId, product.getId());
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // assert
        ProductModel updated = productJpaRepository.findById(product.getId()).orElseThrow();
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(updated.getLikeCount()).isEqualTo(threadCount);
    }

    @DisplayName("동일한 쿠폰으로 두 createOrder가 동시에 요청되면, 쿠폰은 단 한번만 사용된다.")
    @Test
    void coupon_isUsedOnlyOnce_whenConcurrentCreateOrdersWithSameCoupon() throws InterruptedException {
        // arrange
        ProductModel product = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, null));
        stockJpaRepository.save(new StockModel(product.getId(), 100));

        CouponModel coupon = couponJpaRepository.save(
            new CouponModel("10% 할인", CouponType.RATE, 10L, null, ZonedDateTime.now().plusDays(30))
        );
        CouponIssueModel issue = couponIssueJpaRepository.save(new CouponIssueModel(coupon.getId(), 1L));

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // act
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    orderFacade.createOrder(1L, "user1", List.of(new OrderItemCommand(product.getId(), 1)), coupon.getId());
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // assert
        CouponIssueModel updatedIssue = couponIssueJpaRepository.findById(issue.getId()).orElseThrow();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(1);
        assertThat(updatedIssue.getStatus()).isEqualTo(CouponStatus.USED);
    }

    @DisplayName("재고 5개 상품에 10건의 createOrder가 동시에 요청되면, 5건만 성공하고 가용 재고는 0이 된다.")
    @Test
    void stock_isReservedCorrectly_whenConcurrentCreateOrders() throws InterruptedException {
        // arrange
        int stock = 5;
        int threadCount = 10;
        ProductModel product = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, null));
        stockJpaRepository.save(new StockModel(product.getId(), stock));

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // act
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    orderFacade.createOrder(1L, "user1", List.of(new OrderItemCommand(product.getId(), 1)));
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // assert
        StockModel updatedStock = stockJpaRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(successCount.get()).isEqualTo(stock);
        assertThat(failCount.get()).isEqualTo(threadCount - stock);
        assertThat(updatedStock.availableStock()).isEqualTo(0);
    }
}
