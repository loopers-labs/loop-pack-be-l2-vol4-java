package com.loopers.application.coupon;

import com.loopers.application.order.OrderApplicationService;
import com.loopers.application.order.OrderCriteria;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.DiscountPolicy;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Stock;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.coupon.CouponTemplateJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("쿠폰 사용 동시성")
@SpringBootTest
class CouponConcurrencyIntegrationTest {

    @Autowired
    private OrderApplicationService orderApplicationService;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private CouponTemplateJpaRepository couponTemplateJpaRepository;

    @Autowired
    private UserCouponJpaRepository userCouponJpaRepository;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("같은 유저가 같은 쿠폰으로 동시에 여러 주문을 넣어도, 쿠폰은 정확히 1건의 주문에만 사용되고 나머지는 롤백된다.")
    @Test
    void concurrentOrders_doNotReuseSameCoupon() throws InterruptedException {
        Long userId = 1L;
        Long brandId = brandJpaRepository.save(Brand.create("브랜드A", "소개")).getId();

        int attempts = 30;
        // 주문마다 '서로 다른' 상품을 쓴다 — 상품 비관락이 요청들을 직렬화하지 못하게 해서
        // 충돌이 오롯이 '같은 쿠폰을 동시에 사용'하는 지점(@Version)에서만 일어나도록 만든다.
        // 각 상품 재고는 1 — 성공한 주문 1건만 그 상품을 0으로 만들고, 실패 주문은 재고가 그대로 롤백돼야 한다.
        List<Long> productIds = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            productIds.add(productJpaRepository.save(
                    Product.create(brandId, "상품" + i, Money.of(1_000L), Stock.of(1))).getId());
        }

        CouponTemplate template = couponTemplateJpaRepository.save(
                CouponTemplate.create("정액 500원", DiscountPolicy.of(DiscountType.FIXED, 500L), 30));
        Long couponId = userCouponJpaRepository.save(
                UserCoupon.issue(userId, template, ZonedDateTime.now())).getId();

        ExecutorService executor = Executors.newFixedThreadPool(16);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(attempts);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        for (int i = 0; i < attempts; i++) {
            final Long productId = productIds.get(i);
            executor.submit(() -> {
                try {
                    startGate.await();
                    orderApplicationService.place(new OrderCriteria.Place(
                            userId, couponId, List.of(new OrderCriteria.Line(productId, 1))));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 도메인 거부(CoreException)·낙관적 락 충돌 등 모두 실패로 집계
                    failureCount.incrementAndGet();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean finished = doneGate.await(30, TimeUnit.SECONDS);
        executor.shutdownNow();
        assertThat(finished).as("모든 주문 시도가 30초 내에 끝나야 한다").isTrue();

        UserCoupon coupon = userCouponJpaRepository.findById(couponId).orElseThrow();
        long ordersCreated = orderJpaRepository.count();
        long stockConsumed = productJpaRepository.findAllById(productIds).stream()
                .mapToLong(p -> 1 - p.getStock().getQuantity())
                .sum();

        assertThat(successCount.get()).as("성공한 주문 수").isEqualTo(1);
        assertThat(failureCount.get()).as("실패한 주문 수").isEqualTo(attempts - 1);
        assertThat(coupon.getStatus()).as("쿠폰 최종 상태").isEqualTo(CouponStatus.USED);
        assertThat(coupon.getOrderId()).as("쿠폰이 사용된 주문은 정확히 하나여야 한다").isNotNull();
        assertThat(ordersCreated).as("실패한 주문은 롤백되어 주문 행은 정확히 1건이어야 한다").isEqualTo(1);
        assertThat(stockConsumed).as("실패한 주문의 재고 차감은 롤백되어 총 차감은 정확히 1이어야 한다").isEqualTo(1);
    }
}
