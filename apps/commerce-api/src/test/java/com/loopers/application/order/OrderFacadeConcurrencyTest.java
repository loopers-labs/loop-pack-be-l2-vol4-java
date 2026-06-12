package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.member.MemberModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.coupon.CouponTemplateJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.infrastructure.member.MemberJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.support.error.CoreException;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrderFacadeConcurrencyTest {

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private CouponService couponService;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private MemberJpaRepository memberJpaRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private CouponTemplateJpaRepository couponTemplateJpaRepository;

    @Autowired
    private UserCouponJpaRepository userCouponJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("동일 상품에 동시에 주문이 들어와도 재고가 정확히 차감된다.")
    @Test
    void concurrent_orders_deduct_stock_correctly() throws InterruptedException {
        // arrange
        BrandModel brand = brandJpaRepository.save(new BrandModel("테스트 브랜드", "brand001", "brand@test.com"));
        ProductModel product = productJpaRepository.save(
                new ProductModel(brand.getId(), "테스트 상품", "상품 설명", 10000L, 10, null)
        );
        MemberModel member = memberJpaRepository.save(
                new MemberModel("user1", "Password1!", "user1@test.com", "김테스트", "19940101")
        );

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // act — 5개 스레드가 동시에 수량 2씩 주문 (총 10개 재고)
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    orderFacade.placeOrder(member.getId(),
                            List.of(new OrderItemRequest(product.getId(), 2)),
                            null);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // assert
        ProductModel updatedProduct = productJpaRepository.findById(product.getId()).orElseThrow();
        assertThat(successCount.get()).isEqualTo(5);
        assertThat(updatedProduct.getStock()).isEqualTo(0);
    }

    @DisplayName("재고보다 많은 동시 주문이 들어오면 재고 범위 내에서만 성공한다.")
    @Test
    void concurrent_orders_fail_when_stock_is_insufficient() throws InterruptedException {
        // arrange
        BrandModel brand = brandJpaRepository.save(new BrandModel("테스트 브랜드", "brand002", "brand2@test.com"));
        ProductModel product = productJpaRepository.save(
                new ProductModel(brand.getId(), "테스트 상품", "상품 설명", 10000L, 5, null)
        );
        MemberModel member = memberJpaRepository.save(
                new MemberModel("user2", "Password1!", "user2@test.com", "김테스트", "19940101")
        );

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // act — 10개 스레드가 수량 1씩 주문 (재고는 5개)
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    orderFacade.placeOrder(member.getId(),
                            List.of(new OrderItemRequest(product.getId(), 1)),
                            null);
                    successCount.incrementAndGet();
                } catch (CoreException e) {
                    // 재고 부족 예외 기대
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // assert
        ProductModel updatedProduct = productJpaRepository.findById(product.getId()).orElseThrow();
        assertThat(successCount.get()).isEqualTo(5);
        assertThat(updatedProduct.getStock()).isEqualTo(0);
    }

    @DisplayName("동일한 쿠폰으로 동시에 주문해도 쿠폰은 단 한 번만 사용된다.")
    @Test
    void concurrent_coupon_use_is_applied_only_once() throws InterruptedException {
        // arrange
        BrandModel brand = brandJpaRepository.save(new BrandModel("테스트 브랜드", "brand003", "brand3@test.com"));
        ProductModel product = productJpaRepository.save(
                new ProductModel(brand.getId(), "테스트 상품", "상품 설명", 10000L, 100, null)
        );
        MemberModel member = memberJpaRepository.save(
                new MemberModel("user3", "Password1!", "user3@test.com", "김테스트", "19940101")
        );
        CouponTemplate template = couponTemplateJpaRepository.save(
                new CouponTemplate("1000원 할인", CouponType.FIXED, 1000L, null, ZonedDateTime.now().plusDays(30))
        );
        UserCoupon userCoupon = userCouponJpaRepository.save(new UserCoupon(member.getId(), template));

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // act — 5개 스레드가 동일 쿠폰으로 동시 주문
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    orderFacade.placeOrder(member.getId(),
                            List.of(new OrderItemRequest(product.getId(), 1)),
                            userCoupon.getId());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // assert — 쿠폰은 단 한 번만 사용되어야 한다
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(4);

        UserCoupon usedCoupon = userCouponJpaRepository.findById(userCoupon.getId()).orElseThrow();
        assertThat(usedCoupon.getStatus().name()).isEqualTo("USED");
    }
}
