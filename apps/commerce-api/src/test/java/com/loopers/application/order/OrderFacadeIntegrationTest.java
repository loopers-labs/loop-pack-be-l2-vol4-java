package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class OrderFacadeIntegrationTest {

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
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private static final ZonedDateTime FUTURE = ZonedDateTime.now().plusDays(30);

    private UserModel savedUser(String loginId) {
        return userJpaRepository.save(new UserModel(loginId, "pw1"));
    }

    private ProductModel savedProduct(long price, int stock) {
        BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", null, null));
        return productJpaRepository.save(new ProductModel(brand.getId(), "에어맥스 90", "신발", price, stock));
    }

    @DisplayName("주문에 쿠폰을 적용할 때,")
    @Nested
    class ApplyCouponToOrder {

        @DisplayName("시나리오 5 - FIXED 쿠폰 적용 시 할인 금액이 차감된 finalPrice로 주문이 생성되고 쿠폰이 USED 상태가 된다.")
        @Test
        void createsOrderWithFixedDiscount_andMarksCouponUsed() {
            // arrange
            UserModel user = savedUser("user1");
            ProductModel product = savedProduct(100_000L, 10);
            CouponModel coupon = couponJpaRepository.save(new CouponModel("5천원 할인", CouponType.FIXED, 5_000L, null, FUTURE));
            UserCouponModel userCoupon = userCouponJpaRepository.save(new UserCouponModel(user.getId(), coupon.getId()));

            // act
            OrderInfo result = orderFacade.createOrder("user1", "pw1",
                List.of(new OrderFacade.OrderRequest(product.getId(), 1)), userCoupon.getId());

            // assert
            assertAll(
                () -> assertThat(result.originalPrice()).isEqualTo(100_000L),
                () -> assertThat(result.discountAmount()).isEqualTo(5_000L),
                () -> assertThat(result.finalPrice()).isEqualTo(95_000L)
            );

            UserCouponModel used = userCouponJpaRepository.findById(userCoupon.getId()).orElseThrow();
            assertThat(used.getStatus()).isEqualTo(UserCouponStatus.USED);
        }

        @DisplayName("시나리오 6 - RATE 쿠폰 적용 시 비율 계산된 finalPrice로 주문이 생성되고 쿠폰이 USED 상태가 된다.")
        @Test
        void createsOrderWithRateDiscount_andMarksCouponUsed() {
            // arrange
            UserModel user = savedUser("user1");
            ProductModel product = savedProduct(100_000L, 10);
            CouponModel coupon = couponJpaRepository.save(new CouponModel("10% 할인", CouponType.RATE, 10L, null, FUTURE));
            UserCouponModel userCoupon = userCouponJpaRepository.save(new UserCouponModel(user.getId(), coupon.getId()));

            // act
            OrderInfo result = orderFacade.createOrder("user1", "pw1",
                List.of(new OrderFacade.OrderRequest(product.getId(), 2)), userCoupon.getId());

            // assert: 200,000 * 10% = 20,000 할인
            assertAll(
                () -> assertThat(result.originalPrice()).isEqualTo(200_000L),
                () -> assertThat(result.discountAmount()).isEqualTo(20_000L),
                () -> assertThat(result.finalPrice()).isEqualTo(180_000L)
            );

            UserCouponModel used = userCouponJpaRepository.findById(userCoupon.getId()).orElseThrow();
            assertThat(used.getStatus()).isEqualTo(UserCouponStatus.USED);
        }

        @DisplayName("시나리오 7 - userCouponId 없이 주문 시 할인 없이 originalPrice == finalPrice로 주문이 생성된다.")
        @Test
        void createsOrderWithoutDiscount_whenNoCouponProvided() {
            // arrange
            UserModel user = savedUser("user1");
            ProductModel product = savedProduct(50_000L, 5);

            // act
            OrderInfo result = orderFacade.createOrder("user1", "pw1",
                List.of(new OrderFacade.OrderRequest(product.getId(), 2)), null);

            // assert
            assertAll(
                () -> assertThat(result.originalPrice()).isEqualTo(100_000L),
                () -> assertThat(result.discountAmount()).isEqualTo(0L),
                () -> assertThat(result.finalPrice()).isEqualTo(100_000L)
            );
        }

        @DisplayName("주문 금액이 쿠폰의 최소 주문 금액 미만이면 BAD_REQUEST 예외가 발생하고 주문이 생성되지 않는다.")
        @Test
        void throwsBadRequest_whenOrderAmountIsBelowCouponMinimum() {
            // arrange
            UserModel user = savedUser("user1");
            ProductModel product = savedProduct(5_000L, 5);
            CouponModel coupon = couponJpaRepository.save(
                new CouponModel("5천원 할인", CouponType.FIXED, 5_000L, 10_000L, FUTURE));
            UserCouponModel userCoupon = userCouponJpaRepository.save(new UserCouponModel(user.getId(), coupon.getId()));

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () ->
                orderFacade.createOrder("user1", "pw1",
                    List.of(new OrderFacade.OrderRequest(product.getId(), 1)), userCoupon.getId())
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("만료된 쿠폰 템플릿으로 주문 시 BAD_REQUEST 예외가 발생하고 주문이 생성되지 않는다.")
        @Test
        void throwsBadRequest_whenCouponTemplateIsExpired() {
            // arrange
            UserModel user = savedUser("user1");
            ProductModel product = savedProduct(50_000L, 5);
            CouponModel coupon = couponJpaRepository.save(
                new CouponModel("5천원 할인", CouponType.FIXED, 5_000L, null, ZonedDateTime.now().minusDays(1)));
            UserCouponModel userCoupon = userCouponJpaRepository.save(new UserCouponModel(user.getId(), coupon.getId()));

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () ->
                orderFacade.createOrder("user1", "pw1",
                    List.of(new OrderFacade.OrderRequest(product.getId(), 1)), userCoupon.getId())
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("시나리오 8 - 존재하지 않는 userCouponId로 주문 시 BAD_REQUEST 예외가 발생하고 주문이 생성되지 않는다.")
        @Test
        void throwsBadRequest_whenUserCouponDoesNotExist() {
            // arrange
            UserModel user = savedUser("user1");
            ProductModel product = savedProduct(50_000L, 5);
            Long nonExistentUserCouponId = 999L;

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () ->
                orderFacade.createOrder("user1", "pw1",
                    List.of(new OrderFacade.OrderRequest(product.getId(), 1)), nonExistentUserCouponId)
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("시나리오 9 - 타인 소유 쿠폰으로 주문 시 BAD_REQUEST 예외가 발생하고 주문이 생성되지 않는다.")
        @Test
        void throwsBadRequest_whenCouponBelongsToAnotherUser() {
            // arrange
            UserModel owner = savedUser("owner");
            UserModel attacker = savedUser("attacker");
            ProductModel product = savedProduct(50_000L, 5);
            CouponModel coupon = couponJpaRepository.save(new CouponModel("5천원 할인", CouponType.FIXED, 5_000L, null, FUTURE));
            UserCouponModel ownerCoupon = userCouponJpaRepository.save(new UserCouponModel(owner.getId(), coupon.getId()));

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () ->
                orderFacade.createOrder("attacker", "pw1",
                    List.of(new OrderFacade.OrderRequest(product.getId(), 1)), ownerCoupon.getId())
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("시나리오 10 - USED 상태 쿠폰으로 주문 시 BAD_REQUEST 예외가 발생하고 주문이 생성되지 않는다.")
        @Test
        void throwsBadRequest_whenCouponAlreadyUsed() {
            // arrange
            UserModel user = savedUser("user1");
            ProductModel product = savedProduct(50_000L, 5);
            CouponModel coupon = couponJpaRepository.save(new CouponModel("5천원 할인", CouponType.FIXED, 5_000L, null, FUTURE));
            UserCouponModel userCoupon = userCouponJpaRepository.save(new UserCouponModel(user.getId(), coupon.getId()));
            userCoupon.use();
            userCouponJpaRepository.save(userCoupon);

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () ->
                orderFacade.createOrder("user1", "pw1",
                    List.of(new OrderFacade.OrderRequest(product.getId(), 1)), userCoupon.getId())
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("시나리오 11 - EXPIRED 상태 쿠폰으로 주문 시 BAD_REQUEST 예외가 발생하고 주문이 생성되지 않는다.")
        @Test
        void throwsBadRequest_whenCouponExpired() {
            // arrange
            UserModel user = savedUser("user1");
            ProductModel product = savedProduct(50_000L, 5);
            CouponModel coupon = couponJpaRepository.save(new CouponModel("5천원 할인", CouponType.FIXED, 5_000L, null, FUTURE));
            UserCouponModel userCoupon = userCouponJpaRepository.save(new UserCouponModel(user.getId(), coupon.getId()));
            userCoupon.expire();
            userCouponJpaRepository.save(userCoupon);

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () ->
                orderFacade.createOrder("user1", "pw1",
                    List.of(new OrderFacade.OrderRequest(product.getId(), 1)), userCoupon.getId())
            );
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("재고를 동시에 차감할 때,")
    @Nested
    class DeductStockConcurrently {

        @DisplayName("같은 상품에 재고 수량을 초과하는 동시 주문이 들어와도, 원자적 차감으로 초과판매 없이 재고만큼만 성공한다.")
        @Test
        void deductsStockWithoutOversell_whenOrderedConcurrently() throws InterruptedException {
            // arrange: 재고 10개 상품에 20개의 동시 주문(각 1개)
            int stock = 10;
            int threadCount = 20;
            ProductModel product = savedProduct(10_000L, stock);
            for (int i = 0; i < threadCount; i++) {
                savedUser("user" + i);
            }

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger();
            AtomicInteger failureCount = new AtomicInteger();

            // act
            for (int i = 0; i < threadCount; i++) {
                String loginId = "user" + i;
                executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        orderFacade.createOrder(loginId, "pw1",
                            List.of(new OrderFacade.OrderRequest(product.getId(), 1)), null);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            readyLatch.await();
            startLatch.countDown();
            doneLatch.await();
            executor.shutdown();

            // assert: 정확히 재고만큼만 성공, 나머지는 재고 부족으로 실패, 최종 재고는 0 (초과판매 없음)
            assertAll(
                () -> assertThat(successCount.get()).isEqualTo(stock),
                () -> assertThat(failureCount.get()).isEqualTo(threadCount - stock),
                () -> assertThat(productJpaRepository.findById(product.getId()).orElseThrow().getStock())
                    .isEqualTo(0)
            );
        }
    }
}
