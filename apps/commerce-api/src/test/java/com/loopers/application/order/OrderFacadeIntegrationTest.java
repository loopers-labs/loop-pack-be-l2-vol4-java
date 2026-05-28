package com.loopers.application.order;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.brand.BrandInfo;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.user.UserEntity;
import com.loopers.domain.user.UserService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class OrderFacadeIntegrationTest {

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private BrandFacade brandFacade;

    @Autowired
    private ProductFacade productFacade;

    @Autowired
    private UserService userService;

    @Autowired
    private InventoryJpaRepository inventoryJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private UserEntity createUser(String loginId) {
        return userService.signup(loginId, "Password1!", "홍길동", LocalDate.of(1990, 1, 1), loginId + "@test.com");
    }

    private ProductInfo createProduct(Long brandId, String name, Long price, int quantity) {
        return productFacade.createProduct(brandId, name, "상품 설명", price, quantity);
    }

    // ─────────────────────────────────────────────
    // createOrder — 주문 생성
    // ─────────────────────────────────────────────

    @DisplayName("주문 생성")
    @Nested
    class CreateOrder {

        @DisplayName("[ECP] 유효한 요청으로 주문 생성 시 PENDING 상태의 OrderInfo를 반환한다.")
        @Test
        void returnsOrderInfo_withPendingStatus_whenRequestIsValid() {
            // arrange
            UserEntity user = createUser("testuser1");
            BrandInfo brand = brandFacade.createBrand("나이키", "스포츠 브랜드");
            ProductInfo product = createProduct(brand.id(), "에어맥스", 100_000L, 10);
            List<OrderItemCommand> commands = List.of(new OrderItemCommand(product.id(), 2));

            // act
            OrderInfo result = orderFacade.createOrder(user.getId(), commands);

            // assert
            assertAll(
                    () -> assertNotNull(result.orderId()),
                    () -> assertEquals(OrderStatus.PENDING, result.status()),
                    () -> assertEquals(200_000L, result.totalAmount()),
                    () -> assertEquals(1, result.items().size()),
                    () -> assertEquals(product.id(), result.items().get(0).productId()),
                    () -> assertEquals("에어맥스", result.items().get(0).productName()),
                    () -> assertEquals(100_000L, result.items().get(0).productPrice()),
                    () -> assertEquals(2, result.items().get(0).quantity())
            );
        }

        @DisplayName("[Error Guessing] 주문 생성 후 재고가 차감된다.")
        @Test
        void deductsInventory_afterOrderCreated() {
            // arrange
            UserEntity user = createUser("testuser1");
            BrandInfo brand = brandFacade.createBrand("나이키", "스포츠 브랜드");
            ProductInfo product = createProduct(brand.id(), "에어맥스", 100_000L, 10);
            List<OrderItemCommand> commands = List.of(new OrderItemCommand(product.id(), 3));

            // act
            orderFacade.createOrder(user.getId(), commands);

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
            UserEntity user = createUser("testuser1");
            List<OrderItemCommand> commands = List.of(new OrderItemCommand(999L, 1));

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> orderFacade.createOrder(user.getId(), commands));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }

        @DisplayName("[ECP] 재고보다 많은 수량을 주문하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityExceedsInventory() {
            // arrange
            UserEntity user = createUser("testuser1");
            BrandInfo brand = brandFacade.createBrand("나이키", "스포츠 브랜드");
            ProductInfo product = createProduct(brand.id(), "에어맥스", 100_000L, 3);
            List<OrderItemCommand> commands = List.of(new OrderItemCommand(product.id(), 5)); // 재고 3, 요청 5

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> orderFacade.createOrder(user.getId(), commands));
            assertEquals(ErrorType.BAD_REQUEST, exception.getErrorType());
        }

        @DisplayName("[ECP] 품절 상품(재고 0)을 주문하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenProductIsOutOfStock() {
            // arrange
            UserEntity user = createUser("testuser1");
            BrandInfo brand = brandFacade.createBrand("나이키", "스포츠 브랜드");
            ProductInfo product = createProduct(brand.id(), "에어맥스", 100_000L, 0);
            List<OrderItemCommand> commands = List.of(new OrderItemCommand(product.id(), 1));

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> orderFacade.createOrder(user.getId(), commands));
            assertEquals(ErrorType.BAD_REQUEST, exception.getErrorType());
        }
    }

    // ─────────────────────────────────────────────
    // getOrder — 주문 단건 조회
    // ─────────────────────────────────────────────

    @DisplayName("주문 단건 조회")
    @Nested
    class GetOrder {

        @DisplayName("[ECP] 본인의 주문을 조회하면 OrderInfo를 반환한다.")
        @Test
        void returnsOrderInfo_whenOrderIsOwnedByUser() {
            // arrange
            UserEntity user = createUser("testuser1");
            BrandInfo brand = brandFacade.createBrand("나이키", "스포츠 브랜드");
            ProductInfo product = createProduct(brand.id(), "에어맥스", 100_000L, 10);
            OrderInfo created = orderFacade.createOrder(user.getId(),
                    List.of(new OrderItemCommand(product.id(), 1)));

            // act
            OrderInfo result = orderFacade.getOrder(user.getId(), created.orderId());

            // assert
            assertAll(
                    () -> assertEquals(created.orderId(), result.orderId()),
                    () -> assertEquals(OrderStatus.PENDING, result.status()),
                    () -> assertEquals(100_000L, result.totalAmount())
            );
        }

        @DisplayName("[ECP] 존재하지 않는 orderId 조회 시 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenOrderNotExists() {
            // arrange
            UserEntity user = createUser("testuser1");

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> orderFacade.getOrder(user.getId(), 999L));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }

        @DisplayName("[ADR-017] 타인의 주문을 조회하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenOrderIsOwnedByOtherUser() {
            // arrange
            UserEntity owner = createUser("testuser1");
            UserEntity other = createUser("testuser2");
            BrandInfo brand = brandFacade.createBrand("나이키", "스포츠 브랜드");
            ProductInfo product = createProduct(brand.id(), "에어맥스", 100_000L, 10);
            OrderInfo ownerOrder = orderFacade.createOrder(owner.getId(),
                    List.of(new OrderItemCommand(product.id(), 1)));

            // act & assert — other 유저가 owner 주문 접근 시 404
            CoreException exception = assertThrows(CoreException.class,
                    () -> orderFacade.getOrder(other.getId(), ownerOrder.orderId()));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }
    }

    // ─────────────────────────────────────────────
    // getOrders — 주문 목록 조회
    // ─────────────────────────────────────────────

    @DisplayName("주문 목록 조회")
    @Nested
    class GetOrders {

        @DisplayName("[ECP] 날짜 필터 없이 조회하면 본인의 전체 주문 목록을 반환한다.")
        @Test
        void returnsAllOrders_whenNoDateFilter() {
            // arrange
            UserEntity user = createUser("testuser1");
            BrandInfo brand = brandFacade.createBrand("나이키", "스포츠 브랜드");
            ProductInfo product = createProduct(brand.id(), "에어맥스", 100_000L, 20);
            orderFacade.createOrder(user.getId(), List.of(new OrderItemCommand(product.id(), 1)));
            orderFacade.createOrder(user.getId(), List.of(new OrderItemCommand(product.id(), 2)));

            // act
            Page<OrderInfo> result = orderFacade.getOrders(user.getId(), null, null, PageRequest.of(0, 20));

            // assert
            assertEquals(2, result.getTotalElements());
        }

        @DisplayName("[ADR-010] startAt/endAt 범위 내 주문만 반환한다.")
        @Test
        void returnsFilteredOrders_whenDateFilterProvided() {
            // arrange
            UserEntity user = createUser("testuser1");
            BrandInfo brand = brandFacade.createBrand("나이키", "스포츠 브랜드");
            ProductInfo product = createProduct(brand.id(), "에어맥스", 100_000L, 10);
            orderFacade.createOrder(user.getId(), List.of(new OrderItemCommand(product.id(), 1)));

            ZonedDateTime startAt = ZonedDateTime.now().minusDays(1);
            ZonedDateTime endAt = ZonedDateTime.now().plusDays(1);

            // act
            Page<OrderInfo> result = orderFacade.getOrders(user.getId(), startAt, endAt, PageRequest.of(0, 20));

            // assert
            assertEquals(1, result.getTotalElements());
        }

        @DisplayName("[ADR-010] 조회 범위 밖의 주문은 반환되지 않는다.")
        @Test
        void returnsEmptyPage_whenOrdersOutsideDateRange() {
            // arrange
            UserEntity user = createUser("testuser1");
            BrandInfo brand = brandFacade.createBrand("나이키", "스포츠 브랜드");
            ProductInfo product = createProduct(brand.id(), "에어맥스", 100_000L, 10);
            orderFacade.createOrder(user.getId(), List.of(new OrderItemCommand(product.id(), 1)));

            ZonedDateTime startAt = ZonedDateTime.now().plusDays(1); // 미래 범위
            ZonedDateTime endAt = ZonedDateTime.now().plusDays(2);

            // act
            Page<OrderInfo> result = orderFacade.getOrders(user.getId(), startAt, endAt, PageRequest.of(0, 20));

            // assert
            assertThat(result.getContent()).isEmpty();
        }
    }

    // ─────────────────────────────────────────────
    // getAdminOrders — Admin 전체 주문 목록 조회
    // ─────────────────────────────────────────────

    @DisplayName("전체 주문 목록 조회 (Admin)")
    @Nested
    class GetAdminOrders {

        @DisplayName("[ECP] 여러 유저의 주문이 모두 반환된다.")
        @Test
        void returnsAllOrders_acrossAllUsers() {
            // arrange
            UserEntity user1 = createUser("testuser1");
            UserEntity user2 = createUser("testuser2");
            BrandInfo brand = brandFacade.createBrand("나이키", "스포츠 브랜드");
            ProductInfo product = createProduct(brand.id(), "에어맥스", 100_000L, 20);
            orderFacade.createOrder(user1.getId(), List.of(new OrderItemCommand(product.id(), 1)));
            orderFacade.createOrder(user2.getId(), List.of(new OrderItemCommand(product.id(), 1)));

            // act
            Page<OrderInfo> result = orderFacade.getAdminOrders(PageRequest.of(0, 20));

            // assert
            assertAll(
                    () -> assertEquals(2, result.getTotalElements()),
                    () -> assertTrue(result.getContent().stream().allMatch(o -> o.userId() != null))
            );
        }

        @DisplayName("[ECP] 주문이 없으면 빈 페이지가 반환된다.")
        @Test
        void returnsEmptyPage_whenNoOrdersExist() {
            // act
            Page<OrderInfo> result = orderFacade.getAdminOrders(PageRequest.of(0, 20));

            // assert
            assertThat(result.getContent()).isEmpty();
        }
    }

    // ─────────────────────────────────────────────
    // getAdminOrder — Admin 주문 단건 조회
    // ─────────────────────────────────────────────

    @DisplayName("주문 단건 조회 (Admin)")
    @Nested
    class GetAdminOrder {

        @DisplayName("[ECP] 임의의 주문을 소유권 검증 없이 조회할 수 있다.")
        @Test
        void returnsOrderInfo_withoutOwnershipCheck() {
            // arrange
            UserEntity user1 = createUser("testuser1");
            BrandInfo brand = brandFacade.createBrand("나이키", "스포츠 브랜드");
            ProductInfo product = createProduct(brand.id(), "에어맥스", 100_000L, 10);
            OrderInfo created = orderFacade.createOrder(user1.getId(),
                    List.of(new OrderItemCommand(product.id(), 1)));

            // act — Admin은 다른 유저의 주문도 조회 가능
            OrderInfo result = orderFacade.getAdminOrder(created.orderId());

            // assert
            assertAll(
                    () -> assertEquals(created.orderId(), result.orderId()),
                    () -> assertEquals(user1.getId(), result.userId())
            );
        }

        @DisplayName("[ECP] 존재하지 않는 orderId 조회 시 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenOrderNotExists() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                    () -> orderFacade.getAdminOrder(999L));
            assertEquals(ErrorType.NOT_FOUND, exception.getErrorType());
        }
    }
}
