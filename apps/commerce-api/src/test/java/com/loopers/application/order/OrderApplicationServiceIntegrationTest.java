package com.loopers.application.order;

import com.loopers.application.brand.BrandApplicationService;
import com.loopers.application.brand.BrandInfo;
import com.loopers.application.coupon.CouponApplicationService;
import com.loopers.application.coupon.CouponInfo;
import com.loopers.application.coupon.CouponTemplateInfo;
import com.loopers.application.product.ProductApplicationService;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.order.OrderStatus;
import com.loopers.application.user.UserApplicationService;
import com.loopers.application.user.UserInfo;
import com.loopers.infrastructure.inventory.InventoryJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class OrderApplicationServiceIntegrationTest {

    @Autowired
    private OrderApplicationService orderApplicationService;

    @Autowired
    private BrandApplicationService brandApplicationService;

    @Autowired
    private ProductApplicationService productApplicationService;

    @Autowired
    private UserApplicationService userApplicationService;

    @Autowired
    private CouponApplicationService couponApplicationService;

    @Autowired
    private InventoryJpaRepository inventoryJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private UserInfo createUser(String loginId) {
        return userApplicationService.signup(loginId, "Password1!", "홍길동", LocalDate.of(1990, 1, 1), loginId + "@test.com");
    }

    private ProductInfo createProduct(String brandId, String name, Long price, int quantity) {
        return productApplicationService.createProduct(brandId, name, "상품 설명", price, quantity);
    }

    private CouponTemplateInfo createCouponTemplate(Long discountValue) {
        return couponApplicationService.createTemplate(
                "테스트 쿠폰", CouponType.FIXED, discountValue, null,
                ZonedDateTime.now().plusDays(30)
        );
    }

    // ─────────────────────────────────────────────
    // createOrder — 쿠폰 없는 주문
    // ─────────────────────────────────────────────

    @DisplayName("쿠폰 없는 주문 생성")
    @Nested
    class CreateOrderWithoutCoupon {

        @DisplayName("[ECP] 유효한 요청으로 주문 생성 시 PENDING 상태의 OrderInfo를 반환한다.")
        @Test
        void returnsOrderInfo_withPendingStatus_whenRequestIsValid() {
            // arrange
            UserInfo user = createUser("testuser1");
            BrandInfo brand = brandApplicationService.createBrand("나이키", "스포츠 브랜드");
            ProductInfo product = createProduct(brand.id(), "에어맥스", 100_000L, 10);
            List<OrderItemCommand> commands = List.of(new OrderItemCommand(product.id(), 2));

            // act
            OrderInfo result = orderApplicationService.createOrder(user.id(), commands, null);

            // assert
            assertAll(
                    () -> assertNotNull(result.orderId()),
                    () -> assertEquals(OrderStatus.PENDING, result.status()),
                    () -> assertEquals(200_000L, result.originalAmount()),
                    () -> assertEquals(0L, result.discountAmount()),
                    () -> assertEquals(200_000L, result.finalAmount()),
                    () -> assertNull(result.couponId()),
                    () -> assertEquals(1, result.items().size()),
                    () -> assertEquals("에어맥스", result.items().get(0).productName())
            );
        }

        @DisplayName("[Error Guessing] 주문 생성 후 재고가 차감된다.")
        @Test
        void deductsInventory_afterOrderCreated() {
            // arrange
            UserInfo user = createUser("testuser1");
            BrandInfo brand = brandApplicationService.createBrand("나이키", "스포츠 브랜드");
            ProductInfo product = createProduct(brand.id(), "에어맥스", 100_000L, 10);

            // act
            orderApplicationService.createOrder(user.id(),
                    List.of(new OrderItemCommand(product.id(), 3)), null);

            // assert
            assertThat(inventoryJpaRepository.findByProductIdAndDeletedAtIsNull(product.id()))
                    .isPresent()
                    .get()
                    .satisfies(inv -> assertEquals(7, inv.getQuantity()));
        }

        @DisplayName("[ECP] 존재하지 않는 productId가 포함된 경우 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductNotExists() {
            // arrange
            UserInfo user = createUser("testuser1");

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> orderApplicationService.createOrder(user.id(),
                            List.of(new OrderItemCommand("999", 1)), null));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }

        @DisplayName("[ECP] 재고보다 많은 수량을 주문하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityExceedsInventory() {
            // arrange
            UserInfo user = createUser("testuser1");
            BrandInfo brand = brandApplicationService.createBrand("나이키", "스포츠 브랜드");
            ProductInfo product = createProduct(brand.id(), "에어맥스", 100_000L, 3);

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> orderApplicationService.createOrder(user.id(),
                            List.of(new OrderItemCommand(product.id(), 5)), null));
            assertEquals(ErrorType.BAD_REQUEST, exception.getErrorType());
        }
    }

    // ─────────────────────────────────────────────
    // createOrder — 쿠폰 적용 주문
    // ─────────────────────────────────────────────

    @DisplayName("쿠폰 적용 주문 생성")
    @Nested
    class CreateOrderWithCoupon {

        @DisplayName("[ADR-032] 쿠폰 적용 시 finalAmount = originalAmount - discountAmount이다.")
        @Test
        void returnsFinalAmount_equalToDiscountedAmount_whenCouponApplied() {
            // arrange
            UserInfo user = createUser("testuser1");
            BrandInfo brand = brandApplicationService.createBrand("나이키", "스포츠 브랜드");
            ProductInfo product = createProduct(brand.id(), "에어맥스", 100_000L, 10);
            CouponTemplateInfo template = createCouponTemplate(10_000L);
            CouponInfo coupon = couponApplicationService.issueCoupon(user.id(), template.templateId());

            // act
            OrderInfo result = orderApplicationService.createOrder(user.id(),
                    List.of(new OrderItemCommand(product.id(), 1)), coupon.couponId());

            // assert
            assertAll(
                    () -> assertEquals(100_000L, result.originalAmount()),
                    () -> assertEquals(10_000L, result.discountAmount()),
                    () -> assertEquals(90_000L, result.finalAmount()),
                    () -> assertEquals(coupon.couponId(), result.couponId())
            );
        }

        @DisplayName("[ECP] 타인의 쿠폰으로 주문하면 FORBIDDEN 예외가 발생한다.")
        @Test
        void throwsForbidden_whenCouponIsOwnedByOtherUser() {
            // arrange
            UserInfo user = createUser("testuser1");
            UserInfo other = createUser("testuser2");
            BrandInfo brand = brandApplicationService.createBrand("나이키", "스포츠");
            ProductInfo product = createProduct(brand.id(), "에어맥스", 100_000L, 10);
            CouponTemplateInfo template = createCouponTemplate(10_000L);
            CouponInfo otherCoupon = couponApplicationService.issueCoupon(other.id(), template.templateId());

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> orderApplicationService.createOrder(user.id(),
                            List.of(new OrderItemCommand(product.id(), 1)), otherCoupon.couponId()));
            assertEquals(ErrorType.FORBIDDEN, exception.getErrorType());
        }

        @DisplayName("[ECP] 이미 사용된 쿠폰으로 주문하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCouponAlreadyUsed() {
            // arrange
            UserInfo user = createUser("testuser1");
            BrandInfo brand = brandApplicationService.createBrand("나이키", "스포츠");
            ProductInfo product = createProduct(brand.id(), "에어맥스", 100_000L, 10);
            CouponTemplateInfo template = createCouponTemplate(10_000L);
            CouponInfo coupon = couponApplicationService.issueCoupon(user.id(), template.templateId());

            // 첫 번째 주문으로 쿠폰 사용
            orderApplicationService.createOrder(user.id(),
                    List.of(new OrderItemCommand(product.id(), 1)), coupon.couponId());

            // act & assert — 두 번째 주문에서 같은 쿠폰 사용 시도
            CoreException exception = assertThrows(CoreException.class,
                    () -> orderApplicationService.createOrder(user.id(),
                            List.of(new OrderItemCommand(product.id(), 1)), coupon.couponId()));
            assertEquals(ErrorType.BAD_REQUEST, exception.getErrorType());
        }
    }

    // ─────────────────────────────────────────────
    // getOrder
    // ─────────────────────────────────────────────

    @DisplayName("주문 단건 조회")
    @Nested
    class GetOrder {

        @DisplayName("[ECP] 본인의 주문을 조회하면 OrderInfo를 반환한다.")
        @Test
        void returnsOrderInfo_whenOrderIsOwnedByUser() {
            // arrange
            UserInfo user = createUser("testuser1");
            BrandInfo brand = brandApplicationService.createBrand("나이키", "스포츠 브랜드");
            ProductInfo product = createProduct(brand.id(), "에어맥스", 100_000L, 10);
            OrderInfo created = orderApplicationService.createOrder(user.id(),
                    List.of(new OrderItemCommand(product.id(), 1)), null);

            // act
            OrderInfo result = orderApplicationService.getOrder(user.id(), created.orderId());

            // assert
            assertAll(
                    () -> assertEquals(created.orderId(), result.orderId()),
                    () -> assertEquals(OrderStatus.PENDING, result.status()),
                    () -> assertEquals(100_000L, result.finalAmount())
            );
        }

        @DisplayName("[ECP] 존재하지 않는 orderId 조회 시 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenOrderNotExists() {
            // arrange
            UserInfo user = createUser("testuser1");

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> orderApplicationService.getOrder(user.id(), "999"));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }
    }

    // ─────────────────────────────────────────────
    // getOrders
    // ─────────────────────────────────────────────

    @DisplayName("주문 목록 조회")
    @Nested
    class GetOrders {

        @DisplayName("[ECP] 날짜 필터 없이 조회하면 본인의 전체 주문 목록을 반환한다.")
        @Test
        void returnsAllOrders_whenNoDateFilter() {
            // arrange
            UserInfo user = createUser("testuser1");
            BrandInfo brand = brandApplicationService.createBrand("나이키", "스포츠 브랜드");
            ProductInfo product = createProduct(brand.id(), "에어맥스", 100_000L, 20);
            orderApplicationService.createOrder(user.id(), List.of(new OrderItemCommand(product.id(), 1)), null);
            orderApplicationService.createOrder(user.id(), List.of(new OrderItemCommand(product.id(), 2)), null);

            // act
            Page<OrderInfo> result = orderApplicationService.getOrders(user.id(), null, null, PageRequest.of(0, 20));

            // assert
            assertEquals(2, result.getTotalElements());
        }
    }

    // ─────────────────────────────────────────────
    // 동시성 — 쿠폰 중복 사용 방지
    // ─────────────────────────────────────────────

    @DisplayName("동시성 — 쿠폰 중복 사용 방지")
    @Nested
    class CouponConcurrency {

        @DisplayName("[ADR-031] 동일 쿠폰으로 여러 기기에서 동시에 주문해도 쿠폰은 단 한 번만 사용된다.")
        @Test
        void onlyOneCouponUseSucceeds_whenConcurrentOrdersWithSameCoupon() throws InterruptedException {
            // arrange
            int threadCount = 5;
            UserInfo user = createUser("couponconcurrent");
            BrandInfo brand = brandApplicationService.createBrand("나이키", "스포츠");
            ProductInfo product = createProduct(brand.id(), "에어맥스", 10_000L, 100);
            CouponTemplateInfo template = createCouponTemplate(1_000L);
            CouponInfo coupon = couponApplicationService.issueCoupon(user.id(), template.templateId());

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        orderApplicationService.createOrder(user.id(),
                                List.of(new OrderItemCommand(product.id(), 1)), coupon.couponId());
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            // act
            startLatch.countDown();
            doneLatch.await();
            executor.shutdown();

            // assert
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(failCount.get()).isEqualTo(threadCount - 1);
        }
    }

    // ─────────────────────────────────────────────
    // 동시성 — 재고 초과 차감 방지
    // ─────────────────────────────────────────────

    @DisplayName("동시성 — 재고 초과 차감 방지")
    @Nested
    class InventoryConcurrency {

        @DisplayName("동일한 상품에 대해 여러 주문이 동시에 요청되어도 재고가 정상적으로 차감된다.")
        @Test
        void inventoryNotOverDeducted_whenConcurrentOrdersForSameProduct() throws InterruptedException {
            // arrange
            int threadCount = 5;
            int initialStock = 1;

            UserInfo[] users = new UserInfo[threadCount];
            for (int i = 0; i < threadCount; i++) {
                users[i] = createUser("inventoryuser" + i);
            }
            BrandInfo brand = brandApplicationService.createBrand("나이키", "스포츠");
            ProductInfo product = createProduct(brand.id(), "에어맥스", 10_000L, initialStock);

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        orderApplicationService.createOrder(users[idx].id(),
                                List.of(new OrderItemCommand(product.id(), 1)), null);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        // expected for threads that fail
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            // act
            startLatch.countDown();
            doneLatch.await();
            executor.shutdown();

            // assert
            assertThat(successCount.get()).isEqualTo(initialStock);
            assertThat(inventoryJpaRepository.findByProductIdAndDeletedAtIsNull(product.id()))
                    .isPresent()
                    .get()
                    .satisfies(inv -> assertThat(inv.getQuantity()).isGreaterThanOrEqualTo(0));
        }
    }
}
