package com.loopers.concurrency;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderItemCommand;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.shared.Money;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 동시성 통합 테스트.
 *
 * 실제 MySQL Testcontainer + 멀티 스레드(CountDownLatch + ExecutorService)로
 * 락 전략이 의도대로 동작하는지 검증한다.
 *
 * - 재고: 비관적 락 (PESSIMISTIC_WRITE) → 순차 직렬화로 차감, 음수 재고 절대 없음
 * - 쿠폰: 낙관적 락 (@Version) → 충돌 시 정확히 1건만 성공
 * - 좋아요: UNIQUE 제약 + 멱등 → 동시 클릭해도 DB row 는 1개
 *
 * 패턴: 모든 워커 스레드가 ready latch 에서 대기 → start latch 로 동시 출발 →
 *      done latch 로 완료 동기화. 진짜 동시 발사를 시뮬레이션한다.
 */
@SpringBootTest
class ConcurrencyIntegrationTest {

    private static final LocalDateTime FAR_FUTURE = LocalDateTime.of(2099, 12, 31, 23, 59);

    @Autowired private OrderFacade orderFacade;
    @Autowired private LikeService likeService;
    @Autowired private ProductRepository productRepository;
    @Autowired private CouponRepository couponRepository;
    @Autowired private UserCouponRepository userCouponRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    /**
     * N 개 스레드를 동시 발사하는 헬퍼.
     * @return [성공 개수, 실패 개수]
     */
    private int[] runConcurrently(int threadCount, Runnable task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    task.run();
                    success.incrementAndGet();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    failure.incrementAndGet();
                } catch (Throwable t) {
                    failure.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();          // 전체 워커가 출발선에 정렬
        start.countDown();      // 동시 출발
        boolean finished = done.await(30, TimeUnit.SECONDS);
        executor.shutdownNow();
        assertThat(finished).as("모든 스레드는 30초 안에 완료되어야 한다").isTrue();
        return new int[]{success.get(), failure.get()};
    }

    @DisplayName("재고 동시 차감 시,")
    @Nested
    class StockConcurrency {

        @DisplayName("재고와 동일한 수의 동시 주문은 모두 성공하고 재고가 정확히 0 이 된다.")
        @Test
        void allOrdersSucceed_andStockBecomesZero_whenExactlyEnoughStock() throws InterruptedException {
            // arrange: 재고 10 인 상품
            Product product = productRepository.save(Product.create("상품A", "설명", Money.of(1_000L), 10, 1L));
            int threadCount = 10;

            // act: 10 명이 동시에 1개씩 주문
            int[] result = runConcurrently(threadCount, () -> {
                long userId = Thread.currentThread().threadId(); // 워커별 다른 userId
                orderFacade.createOrder(userId, List.of(new OrderItemCommand(product.getId(), 1)), null);
            });

            // assert
            assertThat(result[0]).as("성공 건수").isEqualTo(threadCount);
            assertThat(result[1]).as("실패 건수").isZero();
            Product after = productRepository.find(product.getId()).orElseThrow();
            assertThat(after.getStock()).as("최종 재고").isZero();
        }

        @DisplayName("재고보다 많은 동시 주문은 재고만큼만 성공하고, 나머지는 실패하며 재고는 음수가 되지 않는다.")
        @Test
        void onlyExactStockSucceeds_andRemainingFail_whenOverbooked() throws InterruptedException {
            // arrange: 재고 5 인 상품에 10명 동시 주문
            Product product = productRepository.save(Product.create("상품B", "설명", Money.of(1_000L), 5, 1L));
            int threadCount = 10;

            // act
            int[] result = runConcurrently(threadCount, () -> {
                long userId = Thread.currentThread().threadId();
                orderFacade.createOrder(userId, List.of(new OrderItemCommand(product.getId(), 1)), null);
            });

            // assert: 정확히 5건 성공, 5건 실패, 재고 0
            assertThat(result[0]).as("성공 건수").isEqualTo(5);
            assertThat(result[1]).as("실패 건수").isEqualTo(5);
            Product after = productRepository.find(product.getId()).orElseThrow();
            assertThat(after.getStock()).as("최종 재고 (음수 X)").isZero();
        }
    }

    @DisplayName("쿠폰 동시 사용 시,")
    @Nested
    class CouponConcurrency {

        @DisplayName("같은 쿠폰으로 동시에 두 번 주문하면, 낙관적 락(@Version) 으로 정확히 1건만 성공한다.")
        @Test
        void onlyOneOrderSucceeds_whenSameCouponUsedConcurrently() throws InterruptedException {
            // arrange: 재고 충분한 상품 + 한 유저가 보유한 1장의 쿠폰
            Product product = productRepository.save(Product.create("상품C", "설명", Money.of(10_000L), 100, 1L));
            Coupon coupon = couponRepository.save(Coupon.create("3천원 할인", CouponType.FIXED, 3_000L, null, FAR_FUTURE));
            long userId = 42L;
            UserCoupon issued = userCouponRepository.save(UserCoupon.issue(userId, coupon.getId()));

            // act: 같은 유저가 같은 쿠폰으로 동시에 2개 주문 시도
            int[] result = runConcurrently(2, () ->
                orderFacade.createOrder(
                    userId,
                    List.of(new OrderItemCommand(product.getId(), 1)),
                    issued.getId()
                )
            );

            // assert: 정확히 1건 성공, 1건 실패 (낙관적 락 충돌 OR 이미 USED CONFLICT)
            assertThat(result[0]).as("성공 건수").isEqualTo(1);
            assertThat(result[1]).as("실패 건수").isEqualTo(1);
            // UserCoupon 은 USED 로 단 한 번만 전이
            UserCoupon afterUsage = userCouponRepository.find(issued.getId()).orElseThrow();
            assertThat(afterUsage.getStatus()).isEqualTo(CouponStatus.USED);
            // 저장된 주문은 1건만
            assertThat(orderRepository.findByUserId(userId)).hasSize(1);
        }
    }

    @DisplayName("좋아요 동시 클릭 시,")
    @Nested
    class LikeConcurrency {

        @DisplayName("같은 (유저, 상품) 으로 5번 동시에 좋아요해도, UNIQUE 제약으로 DB 에는 1행만 저장된다.")
        @Test
        void onlyOneLikeRowExists_whenSameUserProductLikedConcurrently() throws InterruptedException {
            // arrange
            Product product = productRepository.save(Product.create("상품D", "설명", Money.of(1_000L), 10, 1L));
            long userId = 77L;

            // act: 5번 동시 좋아요. 중복은 멱등하게 흡수되어야 한다.
            // (race window 시 일부 스레드는 DataIntegrityViolation 으로 실패할 수 있다 — 그게 정상)
            runConcurrently(5, () -> likeService.like(userId, product.getId()));

            // assert: 결과는 항상 1행
            long count = likeService.getLikeCount(product.getId());
            assertThat(count).as("좋아요 행 개수").isEqualTo(1L);
        }
    }
}
