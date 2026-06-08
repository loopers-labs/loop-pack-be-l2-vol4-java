package com.loopers.concurrency;

import com.loopers.application.coupon.CouponApplicationService;
import com.loopers.application.order.OrderApplicationService;
import com.loopers.application.order.OrderItemRequest;
import com.loopers.domain.brand.model.Brand;
import com.loopers.domain.brand.repository.BrandRepository;
import com.loopers.domain.coupon.model.CouponStatus;
import com.loopers.domain.coupon.model.CouponType;
import com.loopers.domain.coupon.model.IssuedCoupon;
import com.loopers.domain.coupon.repository.IssuedCouponRepository;
import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.service.MemberService;
import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.repository.ProductRepository;
import com.loopers.domain.stock.model.Stock;
import com.loopers.domain.stock.repository.StockRepository;
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

@SpringBootTest
class CouponConcurrencyTest {

    @Autowired
    private CouponApplicationService couponApplicationService;

    @Autowired
    private OrderApplicationService orderApplicationService;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @Autowired
    private MemberService memberService;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Member member;
    private Product product;
    private Long issuedCouponId;

    @BeforeEach
    void setUp() {
        member = memberService.register("couponUser", "Password1!", "쿠폰유저", "1990-01-01", "coupon@test.com");

        Brand brand = brandRepository.save(Brand.create("나이키"));
        product = productRepository.save(Product.create(brand.getId(), "에어맥스", "운동화", 100_000L));
        stockRepository.save(Stock.create(product.getId(), 100));

        // 쿠폰 템플릿 생성 및 발급
        var template = couponApplicationService.createTemplate(
            "10% 할인", CouponType.RATE, 10L, null, ZonedDateTime.now().plusDays(30)
        );
        couponApplicationService.issueCoupon(member.getLoginId(), template.getId());

        List<IssuedCoupon> issued = issuedCouponRepository.findAllByMemberId(member.getId());
        issuedCouponId = issued.get(0).getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("동일한 쿠폰으로 동시에 여러 주문을 시도해도, 쿠폰은 단 한 번만 사용된다.")
    @Test
    void coupon_isUsedOnlyOnce_underConcurrentOrders() throws InterruptedException {
        int threadCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        final Long couponId = issuedCouponId;

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    orderApplicationService.createOrder(
                        member.getLoginId(),
                        List.of(new OrderItemRequest(product.getId(), 1)),
                        couponId
                    );
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        IssuedCoupon used = issuedCouponRepository.findById(issuedCouponId).orElseThrow();
        assertThat(used.getStatus()).isEqualTo(CouponStatus.USED);
        assertThat(successCount.get()).isEqualTo(1);
    }
}
