package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCouponModel;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.domain.user.Gender;
import com.loopers.domain.user.PasswordEncryptor;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class OrderConcurrencyIntegrationTest {

    private static final int THREAD_COUNT = 10;
    private static final String LOGIN_ID = "concurr01";
    private static final String LOGIN_PW = "Password1!";

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private CouponTemplateRepository couponTemplateRepository;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @Autowired
    private PasswordEncryptor passwordEncryptor;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private UserModel saveUser() {
        return userRepository.save(new UserModel(
                LOGIN_ID, LOGIN_PW, "홍길동", "1990-01-01",
                "orderuser@example.com", Gender.MALE, passwordEncryptor));
    }

    private ProductModel saveProduct(BigDecimal price) {
        BrandModel brand = brandRepository.save(new BrandModel("브랜드"));
        return productRepository.save(new ProductModel(brand.getId(), "상품", price));
    }

    private void saveStock(Long productId, long quantity) {
        stockRepository.save(new StockModel(productId, quantity));
    }

    @DisplayName("쿠폰 동시 사용 시,")
    @Nested
    class CouponConcurrency {

        @DisplayName("동일한 쿠폰으로 동시에 주문해도, 쿠폰은 단 한번만 사용된다.")
        @Test
        void usesCouponOnlyOnce_whenSameCouponUsedConcurrently() throws InterruptedException {
            // given
            UserModel user = saveUser();
            ProductModel product = saveProduct(BigDecimal.valueOf(10000));
            saveStock(product.getId(), (long) THREAD_COUNT * 10);

            CouponTemplateModel template = couponTemplateRepository.save(
                    new CouponTemplateModel("쿠폰", CouponType.FIXED, BigDecimal.valueOf(1000), null, ZonedDateTime.now().plusDays(1)));
            IssuedCouponModel issued = issuedCouponRepository.save(new IssuedCouponModel(template.getId(), user.getId()));
            Long issuedCouponId = issued.getId();
            Long productId = product.getId();

            // when
            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(THREAD_COUNT);
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger conflictCount = new AtomicInteger(0);

            for (int i = 0; i < THREAD_COUNT; i++) {
                executor.submit(() -> {
                    try {
                        startGate.await();
                        orderFacade.createOrder(LOGIN_ID, LOGIN_PW,
                                List.of(new OrderFacade.OrderItemDto(productId, 1L)), issuedCouponId);
                        successCount.incrementAndGet();
                    } catch (CoreException e) {
                        if (e.getErrorType() == ErrorType.CONFLICT) {
                            conflictCount.incrementAndGet();
                        }
                    } catch (ObjectOptimisticLockingFailureException e) {
                        conflictCount.incrementAndGet();
                    } catch (Exception ignored) {
                    } finally {
                        done.countDown();
                    }
                });
            }

            startGate.countDown();
            done.await();
            executor.shutdown();

            // then
            IssuedCouponModel usedCoupon = issuedCouponRepository.findById(issuedCouponId).orElseThrow();
            assertAll(
                    () -> assertThat(successCount.get()).isEqualTo(1),
                    () -> assertThat(conflictCount.get()).isEqualTo(THREAD_COUNT - 1),
                    () -> assertThat(usedCoupon.getStatus()).isEqualTo(CouponStatus.USED)
            );
        }
    }

    @DisplayName("재고 동시 차감 시,")
    @Nested
    class StockConcurrency {

        @DisplayName("동일한 상품에 동시 주문이 재고보다 많이 들어와도, 재고는 음수가 되지 않고 정상 차감된다.")
        @Test
        void doesNotOversellStock_whenConcurrentOrdersExceedStock() throws InterruptedException {
            // given
            int availableStock = 5;
            ProductModel product = saveProduct(BigDecimal.valueOf(10000));
            saveStock(product.getId(), availableStock);
            Long productId = product.getId();

            List<String> loginIds = new ArrayList<>();
            for (int i = 0; i < THREAD_COUNT; i++) {
                String loginId = String.format("buyer%02d", i);
                userRepository.save(new UserModel(
                        loginId, LOGIN_PW, "유저" + i, "1990-01-01",
                        "buyer" + i + "@example.com", Gender.MALE, passwordEncryptor));
                loginIds.add(loginId);
            }

            // when
            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(THREAD_COUNT);
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

            AtomicInteger successCount = new AtomicInteger(0);

            for (String loginId : loginIds) {
                executor.submit(() -> {
                    try {
                        startGate.await();
                        orderFacade.createOrder(loginId, LOGIN_PW,
                                List.of(new OrderFacade.OrderItemDto(productId, 1L)), null);
                        successCount.incrementAndGet();
                    } catch (Exception ignored) {
                    } finally {
                        done.countDown();
                    }
                });
            }

            startGate.countDown();
            done.await();
            executor.shutdown();

            // then
            StockModel stock = stockRepository.findByProductId(productId).orElseThrow();
            assertAll(
                    () -> assertThat(successCount.get()).isEqualTo(availableStock),
                    () -> assertThat(stock.getQuantity()).isEqualTo(0L)
            );
        }
    }
}
