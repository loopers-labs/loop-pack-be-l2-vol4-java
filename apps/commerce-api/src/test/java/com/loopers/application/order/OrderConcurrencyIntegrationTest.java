package com.loopers.application.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.user.PasswordEncrypter;
import com.loopers.domain.user.UserModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest
class OrderConcurrencyIntegrationTest {

    private static final String RAW_PASSWORD = "Kyle!2030";

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private CouponJpaRepository couponJpaRepository;

    @Autowired
    private UserCouponJpaRepository userCouponJpaRepository;

    @Autowired
    private PasswordEncrypter passwordEncrypter;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private UserModel saveUser(String loginId) {
        return userJpaRepository.save(UserModel.builder()
            .rawLoginId(loginId)
            .rawPassword(RAW_PASSWORD)
            .rawName("테스트유저")
            .rawBirthDate(LocalDate.of(1995, 3, 21))
            .rawEmail(loginId + "@example.com")
            .passwordEncrypter(passwordEncrypter)
            .build());
    }

    private BrandModel saveBrand(String name) {
        return brandJpaRepository.save(BrandModel.builder()
            .rawName(name)
            .rawDescription("감성을 담은 브랜드")
            .build());
    }

    private ProductModel saveProduct(Long brandId, int price, int stock) {
        return productJpaRepository.save(ProductModel.builder()
            .brandId(brandId)
            .rawName("감성 가디건")
            .rawDescription("포근한 감성 가디건")
            .rawPrice(price)
            .rawStock(stock)
            .build());
    }

    private UserCouponModel saveUserCoupon(Long userId) {
        CouponModel coupon = couponJpaRepository.save(CouponModel.builder()
            .rawName("5천원 할인 쿠폰")
            .type(DiscountType.FIXED)
            .rawValue(5_000)
            .rawMinOrderAmount(10_000)
            .rawExpiredAt(ZonedDateTime.now().plusDays(7))
            .now(ZonedDateTime.now())
            .build());

        return userCouponJpaRepository.save(UserCouponModel.issue(userId, coupon));
    }

    @DisplayName("동일 상품에 동시 주문이 몰릴 때,")
    @Nested
    class ConcurrentStockDecrease {

        @DisplayName("재고가 1개면 주문은 단 1건만 성공하고 재고는 0이 된다.")
        @Test
        void decreasesStockExactlyOnce_underConcurrentOrders() throws InterruptedException {
            // arrange (재고 1개 상품에 10명이 동시에 1개씩 주문)
            UserModel user = saveUser("kylekim");
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel product = saveProduct(brand.getId(), 39_000, 1);
            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(16);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger();
            ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();

            // act
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        orderFacade.createOrder(
                            user.getId(),
                            List.of(new OrderItemCommand(product.getId(), 1)),
                            null,
                            ZonedDateTime.now()
                        );
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failures.add(e);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // assert
            ProductModel reloadedProduct = productJpaRepository.findById(product.getId()).orElseThrow();
            assertAll(
                () -> assertThat(successCount.get()).isEqualTo(1),
                () -> assertThat(reloadedProduct.getStock().value()).isEqualTo(0),
                () -> assertThat(failures).allSatisfy(failure -> assertThat(failure)
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType").isEqualTo(ErrorType.CONFLICT))
            );
        }
    }

    @DisplayName("동일 쿠폰으로 동시 주문이 몰릴 때,")
    @Nested
    class ConcurrentCouponUse {

        @DisplayName("쿠폰은 단 1건의 주문에만 사용되고 나머지는 실패한다.")
        @Test
        void usesCouponExactlyOnce_underConcurrentOrders() throws InterruptedException {
            // arrange (발급 쿠폰 1장으로 같은 회원이 10번 동시 주문 — 재고는 충분)
            UserModel user = saveUser("kylekim");
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel product = saveProduct(brand.getId(), 39_000, 100);
            UserCouponModel userCoupon = saveUserCoupon(user.getId());
            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(16);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger();
            ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();

            // act
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        orderFacade.createOrder(
                            user.getId(),
                            List.of(new OrderItemCommand(product.getId(), 1)),
                            userCoupon.getId(),
                            ZonedDateTime.now()
                        );
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failures.add(e);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // assert
            assertAll(
                () -> assertThat(successCount.get()).isEqualTo(1),
                () -> assertThat(failures).allSatisfy(failure -> assertThat(failure)
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType").isEqualTo(ErrorType.CONFLICT))
            );
        }
    }
}
