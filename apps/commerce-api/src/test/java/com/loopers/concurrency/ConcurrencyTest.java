package com.loopers.concurrency;

import com.loopers.application.like.LikeService;
import com.loopers.application.order.OrderCreateCommand;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderItemCommand;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.stock.StockModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.stock.StockJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
class ConcurrencyTest {

    @Autowired private LikeService likeService;
    @Autowired private OrderFacade orderFacade;
    @Autowired private BrandJpaRepository brandJpaRepository;
    @Autowired private ProductJpaRepository productJpaRepository;
    @Autowired private StockJpaRepository stockJpaRepository;
    @Autowired private CouponJpaRepository couponJpaRepository;
    @Autowired private UserCouponJpaRepository userCouponJpaRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private ProductModel savedProduct;

    @BeforeEach
    void setUp() {
        BrandModel brand = brandJpaRepository.save(new BrandModel("Nike", "스포츠 브랜드"));
        savedProduct = productJpaRepository.save(new ProductModel(brand, "나이키 에어맥스", 10_000));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("동일한 상품에 여러 사용자가 동시에 좋아요를 눌러도 좋아요 수가 정상 반영된다.")
    @Test
    void concurrentLikes_allReflectedCorrectly() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();

        for (long userId = 1; userId <= threadCount; userId++) {
            final long uid = userId;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    likeService.like(uid, savedProduct.getId());
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

        long likeCount = productJpaRepository.findById(savedProduct.getId())
            .orElseThrow().getLikeCount();

        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(likeCount).isEqualTo(threadCount);
    }

    @DisplayName("동일한 쿠폰으로 여러 스레드가 동시에 주문해도 쿠폰은 단 한 번만 사용된다.")
    @Test
    void concurrentCouponUse_onlyOneSucceeds() throws InterruptedException {
        stockJpaRepository.save(new StockModel(savedProduct, 100));

        CouponModel coupon = couponJpaRepository.save(
            new CouponModel("10% 할인", CouponType.RATE, 10, null, ZonedDateTime.now().plusDays(30))
        );
        long userId = 1L;
        UserCouponModel userCoupon = userCouponJpaRepository.save(new UserCouponModel(userId, coupon));

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();

        OrderCreateCommand command = new OrderCreateCommand(
            userId,
            List.of(new OrderItemCommand(savedProduct.getId(), 1)),
            userCoupon.getId()
        );

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    orderFacade.createOrder(command);
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                    // 낙관적 락 충돌(ObjectOptimisticLockingFailureException) 또는 이미 사용된 쿠폰 예외
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);

        UserCouponModel result = userCouponJpaRepository.findByIdWithCoupon(userCoupon.getId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(UserCouponStatus.USED);
    }

    @DisplayName("동일한 상품에 여러 주문이 동시에 요청되어도 재고 이상으로 주문이 성공하지 않는다.")
    @Test
    void concurrentStockDecrement_noOverselling() throws InterruptedException {
        int initialStock = 5;
        int threadCount = 10;
        stockJpaRepository.save(new StockModel(savedProduct, initialStock));

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();

        for (long userId = 1; userId <= threadCount; userId++) {
            final long uid = userId;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    OrderCreateCommand command = new OrderCreateCommand(
                        uid,
                        List.of(new OrderItemCommand(savedProduct.getId(), 1)),
                        null
                    );
                    orderFacade.createOrder(command);
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                    // 재고 부족 시 BAD_REQUEST 예외
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(initialStock);

        int remaining = stockJpaRepository.findByProduct_Id(savedProduct.getId())
            .orElseThrow().getQuantity();
        assertThat(remaining).isEqualTo(0);
    }
}
