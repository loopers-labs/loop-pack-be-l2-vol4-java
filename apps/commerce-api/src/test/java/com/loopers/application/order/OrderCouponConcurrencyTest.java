package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.coupon.UserCouponService;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.PaymentMethod;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.stock.StockService;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UC-20 동일 쿠폰 동시 사용 — 정확히 한 건만 성공해야 한다(재사용 방지).
 * 두 주문이 같은 발급분을 동시에 사용하려 할 때:
 * - 동시에 사용 가능 발급분을 읽으면 → 낙관적 락(@Version)이 두 번째 use를 거부(§5-A)
 * - 순차 실행되면 → 두 번째는 이미 USED라 발급분을 못 찾아 실패
 * 어느 인터리빙이든 성공은 정확히 1건.
 */
@SpringBootTest
public class OrderCouponConcurrencyTest {

    @Autowired OrderFacade orderFacade;
    @Autowired BrandService brandService;
    @Autowired ProductService productService;
    @Autowired CouponService couponService;
    @Autowired UserCouponService userCouponService;
    @Autowired UserCouponRepository userCouponRepository;
    @Autowired StockService stockService;
    @Autowired DatabaseCleanUp databaseCleanUp;

    private static final Long USER_ID = 100L;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("같은 쿠폰으로 두 주문을 동시에 넣어도 정확히 한 건만 성공한다")
    @Test
    void given_sameCoupon_when_concurrentOrders_then_onlyOneSucceeds() throws InterruptedException {
        // Arrange — 재고 넉넉, 쿠폰 1장 발급
        BrandModel brand = brandService.register("나이키", "스포츠");
        ProductModel product = productService.createProduct(brand.getId(), "에어맥스", "러닝화", null, 10000L);
        stockService.initialize(product.getId(), 100);
        CouponModel template = couponService.register("할인", CouponType.RATE, 10L, null, ZonedDateTime.now().plusDays(7));
        Long couponId = template.getId();
        userCouponService.issue(USER_ID, couponId);

        // Act — 두 스레드가 동시에 같은 쿠폰으로 주문
        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    orderFacade.placeOrder(USER_ID, PaymentMethod.CARD,
                            List.of(new OrderLine(product.getId(), 1)), couponId);
                    success.incrementAndGet();
                } catch (Throwable t) {
                    failure.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        start.countDown();
        done.await();
        pool.shutdown();

        // Assert — 정확히 한 건만 성공, 쿠폰은 더 이상 사용 불가
        assertThat(success.get()).isEqualTo(1);
        assertThat(failure.get()).isEqualTo(1);
        assertThat(userCouponRepository.findFirstAvailable(USER_ID, couponId)).isEmpty();
    }
}
