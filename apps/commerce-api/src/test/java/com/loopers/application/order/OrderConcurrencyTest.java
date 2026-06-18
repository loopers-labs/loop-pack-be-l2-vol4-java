package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.infrastructure.brand.BrandEntity;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.coupon.CouponEntity;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponEntity;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.infrastructure.product.ProductEntity;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("concurrency")
@SpringBootTest
class OrderConcurrencyTest {

    @Autowired private OrderApplicationService orderApplicationService;
    @Autowired private BrandJpaRepository brandJpaRepository;
    @Autowired private ProductJpaRepository productJpaRepository;
    @Autowired private CouponJpaRepository couponJpaRepository;
    @Autowired private UserCouponJpaRepository userCouponJpaRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("재고 차감 동시성 테스트 - 비관적 락")
    @Nested
    class StockDeduction {

        @DisplayName("재고(5)보다 많은 요청(10)이 동시에 들어와도 정확히 재고 수만큼만 주문이 성공한다.")
        @Test
        void exactlyStockCountSucceeds_underConcurrentOrders() throws InterruptedException {
            int STOCK = 5;
            int THREADS = 10;

            BrandEntity brand = brandJpaRepository.save(BrandEntity.from(new BrandModel("브랜드A", "설명")));
            ProductEntity product = productJpaRepository.save(ProductEntity.from(
                new ProductModel(null, brand.getId(), "상품A", "설명", 10_000L, STOCK, null, null), brand
            ));
            Long productId = product.getId();

            AtomicInteger successCount = new AtomicInteger();
            AtomicInteger failCount = new AtomicInteger();
            CountDownLatch ready = new CountDownLatch(THREADS);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(THREADS);
            ExecutorService executor = Executors.newFixedThreadPool(THREADS);

            for (int i = 0; i < THREADS; i++) {
                final long userId = i + 1L;
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        OrderCommand command = new OrderCommand(
                            List.of(new OrderCommand.Item(productId, 1)), null
                        );
                        orderApplicationService.createOrder(userId, command);
                        successCount.incrementAndGet();
                    } catch (CoreException e) {
                        if (e.getErrorType() == ErrorType.BAD_REQUEST) failCount.incrementAndGet();
                    } catch (Exception ignored) {
                    } finally {
                        done.countDown();
                    }
                });
            }

            ready.await();
            start.countDown();
            done.await();
            executor.shutdown();

            assertThat(successCount.get()).isEqualTo(STOCK);
            assertThat(failCount.get()).isEqualTo(THREADS - STOCK);
        }
    }

    @DisplayName("쿠폰 중복 사용 동시성 테스트")
    @Nested
    class CouponUse {

        @DisplayName("같은 쿠폰을 동시에 5번 사용하려 해도 정확히 1번만 성공한다.")
        @Test
        void exactlyOnceSucceeds_underConcurrentCouponUse() throws InterruptedException {
            int THREADS = 5;
            Long userId = 1L;

            BrandEntity brand = brandJpaRepository.save(BrandEntity.from(new BrandModel("브랜드A", "설명")));

            CouponEntity coupon = couponJpaRepository.save(CouponEntity.from(
                new CouponModel("10% 할인", CouponType.RATE, 10, 0, ZonedDateTime.now().plusDays(7))
            ));
            UserCouponEntity userCoupon = userCouponJpaRepository.save(UserCouponEntity.from(
                new UserCouponModel(userId, coupon.getId())
            ));
            Long userCouponId = userCoupon.getId();

            // 스레드마다 별도 상품 사용 — 상품 비관적 락 경합을 제거해 쿠폰 낙관적 락을 실제로 검증
            List<Long> productIds = new java.util.ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                ProductEntity p = productJpaRepository.save(ProductEntity.from(
                    new ProductModel(null, brand.getId(), "상품" + i, "설명", 10_000L, 100, null, null), brand
                ));
                productIds.add(p.getId());
            }

            AtomicInteger successCount = new AtomicInteger();
            AtomicInteger failCount = new AtomicInteger();
            CountDownLatch ready = new CountDownLatch(THREADS);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(THREADS);
            ExecutorService executor = Executors.newFixedThreadPool(THREADS);

            for (int i = 0; i < THREADS; i++) {
                final Long pid = productIds.get(i);
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        OrderCommand command = new OrderCommand(
                            List.of(new OrderCommand.Item(pid, 1)), userCouponId
                        );
                        orderApplicationService.createOrder(userId, command);
                        successCount.incrementAndGet();
                    } catch (CoreException e) {
                        failCount.incrementAndGet();
                    } catch (ObjectOptimisticLockingFailureException e) {
                        failCount.incrementAndGet();
                    } catch (Exception ignored) {
                    } finally {
                        done.countDown();
                    }
                });
            }

            ready.await();
            start.countDown();
            done.await();
            executor.shutdown();

            assertThat(successCount.get()).isEqualTo(1);
            assertThat(failCount.get()).isEqualTo(THREADS - 1);
        }
    }
}
