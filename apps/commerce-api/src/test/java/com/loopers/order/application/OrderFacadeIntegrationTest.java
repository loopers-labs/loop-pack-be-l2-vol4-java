package com.loopers.order.application;

import com.loopers.brand.domain.Brand;
import com.loopers.brand.domain.BrandService;
import com.loopers.coupon.domain.CouponTemplate;
import com.loopers.coupon.domain.CouponTemplateRepository;
import com.loopers.coupon.domain.CouponType;
import com.loopers.coupon.domain.UserCoupon;
import com.loopers.coupon.domain.UserCouponRepository;
import com.loopers.coupon.domain.UserCouponStatus;
import com.loopers.coupon.domain.policy.FixedCouponDiscountPolicy;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductService;
import com.loopers.stock.domain.ProductStockService;
import com.loopers.order.infrastructure.OrderJpaRepository;
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
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class OrderFacadeIntegrationTest {

    private static final String COUPON_NAME = "1주년 정액 할인 쿠폰";
    private static final ZonedDateTime EXPIRED_AT = ZonedDateTime.parse("2026-12-31T23:59:59+09:00");
    private static final FixedCouponDiscountPolicy FIXED_POLICY = new FixedCouponDiscountPolicy();

    private final OrderFacade orderFacade;
    private final BrandService brandService;
    private final ProductService productService;
    private final ProductStockService productStockService;
    private final CouponTemplateRepository couponTemplateRepository;
    private final UserCouponRepository userCouponRepository;
    private final OrderJpaRepository orderJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    OrderFacadeIntegrationTest(
        OrderFacade orderFacade,
        BrandService brandService,
        ProductService productService,
        ProductStockService productStockService,
        CouponTemplateRepository couponTemplateRepository,
        UserCouponRepository userCouponRepository,
        OrderJpaRepository orderJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.orderFacade = orderFacade;
        this.brandService = brandService;
        this.productService = productService;
        this.productStockService = productStockService;
        this.couponTemplateRepository = couponTemplateRepository;
        this.userCouponRepository = userCouponRepository;
        this.orderJpaRepository = orderJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("주문을 생성할 때 ")
    @Nested
    class CreateOrder {

        @DisplayName("주문 가능한 상품과 수량이 주어지면, 재고를 차감하고 주문 스냅샷을 저장한다.")
        @Test
        void createsOrderAndDeductsStock_whenProductsAreOrderable() {
            // arrange
            Long userId = 1L;
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            Product iphone = productService.createProduct(
                brand.getId(),
                "아이폰 16 Pro",
                "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
                1_550_000L
            );
            Product iphoneMax = productService.createProduct(
                brand.getId(),
                "아이폰 16 Pro Max",
                "더 큰 화면과 향상된 배터리를 제공하는 스마트폰",
                1_900_000L
            );
            productStockService.createProductStock(iphone.getId(), 10);
            productStockService.createProductStock(iphoneMax.getId(), 5);
            CreateOrderCommand command = new CreateOrderCommand(userId, List.of(
                new CreateOrderCommand.Item(iphone.getId(), 2),
                new CreateOrderCommand.Item(iphoneMax.getId(), 1)
            ));

            // act
            OrderInfo result = orderFacade.createOrder(command);

            // assert
            assertAll(
                () -> assertThat(result.id()).isNotNull(),
                () -> assertThat(result.userId()).isEqualTo(userId),
                () -> assertThat(result.orderTotalPrice()).isEqualTo(5_000_000L),
                () -> assertThat(result.items())
                    .extracting(OrderInfo.Item::productName)
                    .containsExactly("아이폰 16 Pro", "아이폰 16 Pro Max"),
                () -> assertThat(result.items())
                    .extracting(OrderInfo.Item::brandName)
                    .containsExactly("애플", "애플"),
                () -> assertThat(result.items())
                    .extracting(OrderInfo.Item::totalPrice)
                    .containsExactly(3_100_000L, 1_900_000L),
                () -> assertThat(orderJpaRepository.count()).isEqualTo(1),
                () -> assertThat(productStockService.getProductStock(iphone.getId()).getQuantity()).isEqualTo(8),
                () -> assertThat(productStockService.getProductStock(iphoneMax.getId()).getQuantity()).isEqualTo(4)
            );
        }

        @DisplayName("여러 상품 ID가 역순으로 주어져도, 재고를 차감하고 주문 스냅샷을 저장한다.")
        @Test
        void createsOrderAndDeductsStock_whenProductIdsAreProvidedInReverseOrder() {
            // arrange
            Long userId = 1L;
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            Product iphone = productService.createProduct(
                brand.getId(),
                "아이폰 16 Pro",
                "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
                1_550_000L
            );
            Product iphoneMax = productService.createProduct(
                brand.getId(),
                "아이폰 16 Pro Max",
                "더 큰 화면과 향상된 배터리를 제공하는 스마트폰",
                1_900_000L
            );
            productStockService.createProductStock(iphone.getId(), 10);
            productStockService.createProductStock(iphoneMax.getId(), 5);
            CreateOrderCommand command = new CreateOrderCommand(userId, List.of(
                new CreateOrderCommand.Item(iphoneMax.getId(), 1),
                new CreateOrderCommand.Item(iphone.getId(), 2)
            ));

            // act
            OrderInfo result = orderFacade.createOrder(command);

            // assert
            assertAll(
                () -> assertThat(result.items())
                    .extracting(OrderInfo.Item::productName)
                    .containsExactly("아이폰 16 Pro Max", "아이폰 16 Pro"),
                () -> assertThat(result.items())
                    .extracting(OrderInfo.Item::totalPrice)
                    .containsExactly(1_900_000L, 3_100_000L),
                () -> assertThat(orderJpaRepository.count()).isEqualTo(1),
                () -> assertThat(productStockService.getProductStock(iphone.getId()).getQuantity()).isEqualTo(8),
                () -> assertThat(productStockService.getProductStock(iphoneMax.getId()).getQuantity()).isEqualTo(4)
            );
        }

        @DisplayName("사용 가능한 쿠폰이 주어지면, 주문 금액을 할인하고 쿠폰을 사용 완료로 변경한다.")
        @Test
        void createsOrderWithDiscountAndUsesCoupon_whenAvailableCouponIsProvided() {
            // arrange
            Long userId = 1L;
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            Product iphone = productService.createProduct(
                brand.getId(),
                "아이폰 16 Pro",
                "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
                1_550_000L
            );
            productStockService.createProductStock(iphone.getId(), 10);
            CouponTemplate couponTemplate = createFixedCouponTemplate();
            UserCoupon userCoupon = userCouponRepository.save(couponTemplate.issue(userId, EXPIRED_AT.minusDays(1)));
            CreateOrderCommand command = new CreateOrderCommand(userId, List.of(
                new CreateOrderCommand.Item(iphone.getId(), 1)
            ), userCoupon.getId());

            // act
            OrderInfo result = orderFacade.createOrder(command);

            // assert
            UserCoupon usedCoupon = userCouponRepository.findIssuedCoupon(userId, couponTemplate.getId())
                .orElseThrow();
            assertAll(
                () -> assertThat(result.id()).isNotNull(),
                () -> assertThat(result.appliedUserCouponId()).isEqualTo(userCoupon.getId()),
                () -> assertThat(result.orderTotalPrice()).isEqualTo(1_550_000L),
                () -> assertThat(result.discountAmount()).isEqualTo(2_000L),
                () -> assertThat(result.paymentAmount()).isEqualTo(1_548_000L),
                () -> assertThat(usedCoupon.getStatus()).isEqualTo(UserCouponStatus.USED),
                () -> assertThat(usedCoupon.getUsedAt()).isNotNull(),
                () -> assertThat(productStockService.getProductStock(iphone.getId()).getQuantity()).isEqualTo(9)
            );
        }

        @DisplayName("이미 사용된 쿠폰이 주어지면, 주문 생성을 실패하고 재고를 차감하지 않는다.")
        @Test
        void throwsConflictAndKeepsStock_whenCouponIsAlreadyUsed() {
            // arrange
            Long userId = 1L;
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            Product iphone = productService.createProduct(
                brand.getId(),
                "아이폰 16 Pro",
                "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
                1_550_000L
            );
            productStockService.createProductStock(iphone.getId(), 10);
            CouponTemplate couponTemplate = createFixedCouponTemplate();
            UserCoupon userCoupon = userCouponRepository.save(couponTemplate.issue(userId, EXPIRED_AT.minusDays(1)));
            userCoupon.use(userId, ZonedDateTime.parse("2026-06-01T12:00:00+09:00"));
            userCouponRepository.save(userCoupon);
            CreateOrderCommand command = new CreateOrderCommand(userId, List.of(
                new CreateOrderCommand.Item(iphone.getId(), 1)
            ), userCoupon.getId());

            // act & assert
            assertAll(
                () -> assertThatThrownBy(() -> orderFacade.createOrder(command))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.CONFLICT),
                () -> assertThat(orderJpaRepository.count()).isZero(),
                () -> assertThat(productStockService.getProductStock(iphone.getId()).getQuantity()).isEqualTo(10)
            );
        }

        @DisplayName("동일한 쿠폰으로 동시에 주문하면, 하나의 주문만 성공하고 쿠폰은 한 번만 사용된다.")
        @Test
        void createsOnlyOneOrder_whenSameCouponIsUsedConcurrently() throws Exception {
            // arrange
            Long userId = 1L;
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            Product iphone = productService.createProduct(
                brand.getId(),
                "아이폰 16 Pro",
                "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
                1_550_000L
            );
            productStockService.createProductStock(iphone.getId(), 2);
            CouponTemplate couponTemplate = createFixedCouponTemplate();
            UserCoupon userCoupon = userCouponRepository.save(couponTemplate.issue(userId, EXPIRED_AT.minusDays(1)));
            CreateOrderCommand firstCommand = new CreateOrderCommand(userId, List.of(
                new CreateOrderCommand.Item(iphone.getId(), 1)
            ), userCoupon.getId());
            CreateOrderCommand secondCommand = new CreateOrderCommand(userId, List.of(
                new CreateOrderCommand.Item(iphone.getId(), 1)
            ), userCoupon.getId());
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);

            try {
                Future<OrderAttempt> first = executor.submit(() -> createOrderAfter(start, firstCommand));
                Future<OrderAttempt> second = executor.submit(() -> createOrderAfter(start, secondCommand));

                // act
                start.countDown();
                List<OrderAttempt> attempts = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
                );

                // assert
                UserCoupon usedCoupon = userCouponRepository.findIssuedCoupon(userId, couponTemplate.getId())
                    .orElseThrow();
                assertAll(
                    () -> assertThat(attempts).filteredOn(OrderAttempt::succeeded).hasSize(1),
                    () -> assertThat(attempts)
                        .filteredOn(attempt -> !attempt.succeeded())
                        .extracting(OrderAttempt::errorType)
                        .containsExactly(ErrorType.CONFLICT),
                    () -> assertThat(orderJpaRepository.count()).isEqualTo(1),
                    () -> assertThat(productStockService.getProductStock(iphone.getId()).getQuantity()).isEqualTo(1),
                    () -> assertThat(usedCoupon.getStatus()).isEqualTo(UserCouponStatus.USED)
                );
            } finally {
                executor.shutdownNow();
            }
        }

        @DisplayName("하나의 상품이라도 재고가 부족하면, 주문 생성을 실패하고 차감된 재고도 롤백한다.")
        @Test
        void throwsConflictAndRollsBackStock_whenAnyProductStockIsInsufficient() {
            // arrange
            Long userId = 1L;
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            Product iphone = productService.createProduct(
                brand.getId(),
                "아이폰 16 Pro",
                "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
                1_550_000L
            );
            Product iphoneMax = productService.createProduct(
                brand.getId(),
                "아이폰 16 Pro Max",
                "더 큰 화면과 향상된 배터리를 제공하는 스마트폰",
                1_900_000L
            );
            productStockService.createProductStock(iphone.getId(), 10);
            productStockService.createProductStock(iphoneMax.getId(), 5);
            CreateOrderCommand command = new CreateOrderCommand(userId, List.of(
                new CreateOrderCommand.Item(iphone.getId(), 2),
                new CreateOrderCommand.Item(iphoneMax.getId(), 6)
            ));

            // act & assert
            assertAll(
                () -> assertThatThrownBy(() -> orderFacade.createOrder(command))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.CONFLICT),
                () -> assertThat(orderJpaRepository.count()).isZero(),
                () -> assertThat(productStockService.getProductStock(iphone.getId()).getQuantity()).isEqualTo(10),
                () -> assertThat(productStockService.getProductStock(iphoneMax.getId()).getQuantity()).isEqualTo(5)
            );
        }

        @DisplayName("재고가 1개인 상품을 동시에 주문하면, 하나의 주문만 성공하고 초과 판매가 발생하지 않는다.")
        @Test
        void createsOnlyOneOrder_whenTwoUsersOrderLastStockConcurrently() throws Exception {
            // arrange
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            Product iphone = productService.createProduct(
                brand.getId(),
                "아이폰 16 Pro",
                "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
                1_550_000L
            );
            productStockService.createProductStock(iphone.getId(), 1);
            CreateOrderCommand firstCommand = new CreateOrderCommand(1L, List.of(
                new CreateOrderCommand.Item(iphone.getId(), 1)
            ));
            CreateOrderCommand secondCommand = new CreateOrderCommand(2L, List.of(
                new CreateOrderCommand.Item(iphone.getId(), 1)
            ));
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);

            try {
                Future<OrderAttempt> first = executor.submit(() -> createOrderAfter(start, firstCommand));
                Future<OrderAttempt> second = executor.submit(() -> createOrderAfter(start, secondCommand));

                // act
                start.countDown();
                List<OrderAttempt> attempts = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
                );

                // assert
                assertAll(
                    () -> assertThat(attempts).filteredOn(OrderAttempt::succeeded).hasSize(1),
                    () -> assertThat(attempts)
                        .filteredOn(attempt -> !attempt.succeeded())
                        .extracting(OrderAttempt::errorType)
                        .containsExactly(ErrorType.CONFLICT),
                    () -> assertThat(orderJpaRepository.count()).isEqualTo(1),
                    () -> assertThat(productStockService.getProductStock(iphone.getId()).getQuantity()).isZero()
                );
            } finally {
                executor.shutdownNow();
            }
        }

        @DisplayName("삭제된 상품이 포함되면, 주문 생성을 실패하고 재고를 차감하지 않는다.")
        @Test
        void throwsNotFoundAndKeepsStock_whenProductIsDeleted() {
            // arrange
            Long userId = 1L;
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            Product iphone = productService.createProduct(
                brand.getId(),
                "아이폰 16 Pro",
                "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
                1_550_000L
            );
            productStockService.createProductStock(iphone.getId(), 10);
            productService.deleteProduct(iphone.getId());
            CreateOrderCommand command = new CreateOrderCommand(userId, List.of(
                new CreateOrderCommand.Item(iphone.getId(), 2)
            ));

            // act & assert
            assertAll(
                () -> assertThatThrownBy(() -> orderFacade.createOrder(command))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.NOT_FOUND),
                () -> assertThat(orderJpaRepository.count()).isZero(),
                () -> assertThat(productStockService.getProductStock(iphone.getId()).getQuantity()).isEqualTo(10)
            );
        }

        @DisplayName("삭제된 브랜드의 상품이 포함되면, 주문 생성을 실패하고 재고를 차감하지 않는다.")
        @Test
        void throwsNotFoundAndKeepsStock_whenProductBrandIsDeleted() {
            // arrange
            Long userId = 1L;
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            Product iphone = productService.createProduct(
                brand.getId(),
                "아이폰 16 Pro",
                "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
                1_550_000L
            );
            productStockService.createProductStock(iphone.getId(), 10);
            brandService.deleteBrand(brand.getId());
            CreateOrderCommand command = new CreateOrderCommand(userId, List.of(
                new CreateOrderCommand.Item(iphone.getId(), 2)
            ));

            // act & assert
            assertAll(
                () -> assertThatThrownBy(() -> orderFacade.createOrder(command))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.NOT_FOUND),
                () -> assertThat(orderJpaRepository.count()).isZero(),
                () -> assertThat(productStockService.getProductStock(iphone.getId()).getQuantity()).isEqualTo(10)
            );
        }

        @DisplayName("존재하지 않는 상품이 포함되면, NOT_FOUND 예외를 던진다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            // arrange
            Long userId = 1L;
            CreateOrderCommand command = new CreateOrderCommand(userId, List.of(
                new CreateOrderCommand.Item(999L, 1)
            ));

            // act & assert
            assertThatThrownBy(() -> orderFacade.createOrder(command))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
            assertThat(orderJpaRepository.count()).isZero();
        }

        @DisplayName("같은 상품 ID가 중복되면, BAD_REQUEST 예외를 던지고 재고를 차감하지 않는다.")
        @Test
        void throwsBadRequestAndKeepsStock_whenProductIdIsDuplicated() {
            // arrange
            Long userId = 1L;
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            Product iphone = productService.createProduct(
                brand.getId(),
                "아이폰 16 Pro",
                "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
                1_550_000L
            );
            productStockService.createProductStock(iphone.getId(), 10);
            CreateOrderCommand command = new CreateOrderCommand(userId, List.of(
                new CreateOrderCommand.Item(iphone.getId(), 1),
                new CreateOrderCommand.Item(iphone.getId(), 2)
            ));

            // act & assert
            assertAll(
                () -> assertThatThrownBy(() -> orderFacade.createOrder(command))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(orderJpaRepository.count()).isZero(),
                () -> assertThat(productStockService.getProductStock(iphone.getId()).getQuantity()).isEqualTo(10)
            );
        }

        @DisplayName("주문 항목이 비어 있으면, BAD_REQUEST 예외를 던진다.")
        @Test
        void throwsBadRequest_whenItemsAreEmpty() {
            // arrange
            Long userId = 1L;
            CreateOrderCommand command = new CreateOrderCommand(userId, List.of());

            // act & assert
            assertThatThrownBy(() -> orderFacade.createOrder(command))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(orderJpaRepository.count()).isZero();
        }

        @DisplayName("주문 수량이 0 이하이면, BAD_REQUEST 예외를 던진다.")
        @Test
        void throwsBadRequest_whenQuantityIsNotPositive() {
            // arrange
            Long userId = 1L;
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            Product iphone = productService.createProduct(
                brand.getId(),
                "아이폰 16 Pro",
                "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
                1_550_000L
            );
            productStockService.createProductStock(iphone.getId(), 10);
            CreateOrderCommand command = new CreateOrderCommand(userId, List.of(
                new CreateOrderCommand.Item(iphone.getId(), 0)
            ));

            // act & assert
            assertThatThrownBy(() -> orderFacade.createOrder(command))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(orderJpaRepository.count()).isZero();
        }
    }

    @DisplayName("내 주문 상세를 조회할 때 ")
    @Nested
    class GetMyOrderDetail {

        @DisplayName("주문 이후 상품이 삭제되어도, 주문 당시 상품 스냅샷을 반환한다.")
        @Test
        void returnsOrderSnapshot_whenProductIsDeletedAfterOrderCreated() {
            // arrange
            Long userId = 1L;
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            Product iphone = productService.createProduct(
                brand.getId(),
                "아이폰 16 Pro",
                "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
                1_550_000L
            );
            productStockService.createProductStock(iphone.getId(), 10);
            OrderInfo order = orderFacade.createOrder(new CreateOrderCommand(userId, List.of(
                new CreateOrderCommand.Item(iphone.getId(), 2)
            )));
            productService.deleteProduct(iphone.getId());

            // act
            OrderInfo found = orderFacade.getMyOrderDetail(order.id(), userId);

            // assert
            assertAll(
                () -> assertThat(found.id()).isEqualTo(order.id()),
                () -> assertThat(found.items()).hasSize(1),
                () -> assertThat(found.items().getFirst().brandName()).isEqualTo("애플"),
                () -> assertThat(found.items().getFirst().productName()).isEqualTo("아이폰 16 Pro"),
                () -> assertThat(found.items().getFirst().unitPrice()).isEqualTo(1_550_000L),
                () -> assertThat(found.items().getFirst().quantity()).isEqualTo(2),
                () -> assertThat(found.items().getFirst().totalPrice()).isEqualTo(3_100_000L)
            );
        }

        @DisplayName("다른 사용자의 주문 ID가 주어지면, FORBIDDEN 예외를 던진다.")
        @Test
        void throwsForbidden_whenOrderBelongsToAnotherUser() {
            // arrange
            Long ownerUserId = 1L;
            Long otherUserId = 2L;
            Brand brand = brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
            Product iphone = productService.createProduct(
                brand.getId(),
                "아이폰 16 Pro",
                "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰",
                1_550_000L
            );
            productStockService.createProductStock(iphone.getId(), 10);
            OrderInfo order = orderFacade.createOrder(new CreateOrderCommand(ownerUserId, List.of(
                new CreateOrderCommand.Item(iphone.getId(), 1)
            )));

            // act & assert
            assertThatThrownBy(() -> orderFacade.getMyOrderDetail(order.id(), otherUserId))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.FORBIDDEN);
        }
    }

    private OrderAttempt createOrderAfter(CountDownLatch start, CreateOrderCommand command) {
        try {
            start.await();
            orderFacade.createOrder(command);
            return OrderAttempt.successAttempt();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("주문 시작 대기 중 인터럽트가 발생했습니다.", e);
        } catch (CoreException e) {
            return OrderAttempt.failureAttempt(e.getErrorType());
        }
    }

    private CouponTemplate createFixedCouponTemplate() {
        return couponTemplateRepository.save(CouponTemplate.create(
            COUPON_NAME,
            CouponType.FIXED,
            2_000L,
            10_000L,
            EXPIRED_AT,
            FIXED_POLICY
        ));
    }

    private record OrderAttempt(boolean succeeded, ErrorType errorType) {

        private static OrderAttempt successAttempt() {
            return new OrderAttempt(true, null);
        }

        private static OrderAttempt failureAttempt(ErrorType errorType) {
            return new OrderAttempt(false, errorType);
        }
    }
}
