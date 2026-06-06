package com.loopers.application.order;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.user.UserCommand;
import com.loopers.application.user.UserFacade;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.infrastructure.order.OrderItemJpaRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.stock.StockJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrderFacadeIntegrationTest {

    private final OrderFacade orderFacade;
    private final BrandFacade brandFacade;
    private final ProductFacade productFacade;
    private final UserFacade userFacade;
    private final OrderJpaRepository orderJpaRepository;
    private final OrderItemJpaRepository orderItemJpaRepository;
    private final StockJpaRepository stockJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    private Long userId;
    private Long productAId;
    private Long productBId;

    @Autowired
    public OrderFacadeIntegrationTest(
        OrderFacade orderFacade,
        BrandFacade brandFacade,
        ProductFacade productFacade,
        UserFacade userFacade,
        OrderJpaRepository orderJpaRepository,
        OrderItemJpaRepository orderItemJpaRepository,
        StockJpaRepository stockJpaRepository,
        ProductJpaRepository productJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.orderFacade = orderFacade;
        this.brandFacade = brandFacade;
        this.productFacade = productFacade;
        this.userFacade = userFacade;
        this.orderJpaRepository = orderJpaRepository;
        this.orderItemJpaRepository = orderItemJpaRepository;
        this.stockJpaRepository = stockJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        Long brandId = brandFacade.create("나이키", "Just Do It").id();
        productAId = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 100_000L, 10, brandId).id();
        productBId = productFacade.createProduct("에어포스 1", "클래식 스니커즈", 150_000L, 10, brandId).id();
        userId = userFacade.signUp(new UserCommand.SignUp(
            "user01",
            "Abcd1234!",
            "김철수",
            LocalDate.of(1999, 3, 22),
            "user@example.com"
        )).id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("주문 생성 시, ")
    @Nested
    class PlaceOrder {

        @DisplayName("정상 단일 라인이면, orders/order_items 행이 생성되고 totalAmount/스냅샷/재고가 모두 반영된다.")
        @Test
        void persistsOrderWithSnapshotsAndDecreasesStock_whenSingleLineIsValid() {
            // given
            OrderCommand.Place command = new OrderCommand.Place(List.of(
                new OrderCommand.Line(productAId, 2)
            ));

            // when
            OrderInfo result = orderFacade.placeOrder(userId, command);

            // then
            List<OrderItem> savedItems = orderItemJpaRepository.findByOrderId(result.id());
            assertAll(
                () -> assertThat(result.userId()).isEqualTo(userId),
                () -> assertThat(result.totalAmount()).isEqualTo(200_000L),
                () -> assertThat(result.status()).isEqualTo(OrderStatus.CREATED),
                () -> assertThat(result.items()).hasSize(1),
                () -> assertThat(orderJpaRepository.count()).isEqualTo(1L),
                () -> assertThat(savedItems).hasSize(1),
                () -> assertThat(savedItems.get(0).getProductName()).isEqualTo("에어맥스 270"),
                () -> assertThat(savedItems.get(0).getProductPrice()).isEqualTo(100_000L),
                () -> assertThat(savedItems.get(0).getBrandName()).isEqualTo("나이키"),
                () -> assertThat(loadStockQuantity(productAId)).isEqualTo(8)
            );
        }

        @DisplayName("다항목 라인이면, orders 1행 + order_items N행 + totalAmount 합계 + 각 상품 재고 감소가 반영된다.")
        @Test
        void persistsMultipleOrderItems_whenMultipleLinesAreValid() {
            // given
            OrderCommand.Place command = new OrderCommand.Place(List.of(
                new OrderCommand.Line(productAId, 1),
                new OrderCommand.Line(productBId, 3)
            ));

            // when
            OrderInfo result = orderFacade.placeOrder(userId, command);

            // then
            assertAll(
                () -> assertThat(result.totalAmount()).isEqualTo(100_000L + 150_000L * 3L),
                () -> assertThat(orderJpaRepository.count()).isEqualTo(1L),
                () -> assertThat(orderItemJpaRepository.findByOrderId(result.id())).hasSize(2),
                () -> assertThat(loadStockQuantity(productAId)).isEqualTo(9),
                () -> assertThat(loadStockQuantity(productBId)).isEqualTo(7)
            );
        }

        @DisplayName("같은 productId 라인이 두 번 들어오면, OrderItem 은 1개로 합산되고 stock 도 합계만큼 감소한다 (DEC-07).")
        @Test
        void mergesDuplicateProductLines_whenSameProductAppearsTwice() {
            // given
            OrderCommand.Place command = new OrderCommand.Place(List.of(
                new OrderCommand.Line(productAId, 2),
                new OrderCommand.Line(productAId, 3)
            ));

            // when
            OrderInfo result = orderFacade.placeOrder(userId, command);

            // then
            List<OrderItem> savedItems = orderItemJpaRepository.findByOrderId(result.id());
            assertAll(
                () -> assertThat(savedItems).hasSize(1),
                () -> assertThat(savedItems.get(0).getQuantity()).isEqualTo(5),
                () -> assertThat(result.totalAmount()).isEqualTo(100_000L * 5L),
                () -> assertThat(loadStockQuantity(productAId)).isEqualTo(5)
            );
        }

        @DisplayName("items 가 비어있으면, EMPTY_ORDER_ITEMS 예외가 발생하고 orders 행이 생성되지 않는다.")
        @Test
        void throwsEmptyOrderItems_whenItemsIsEmpty() {
            // given
            OrderCommand.Place command = new OrderCommand.Place(List.of());

            // when
            CoreException exception = assertThrows(CoreException.class,
                () -> orderFacade.placeOrder(userId, command));

            // then
            assertAll(
                () -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.EMPTY_ORDER_ITEMS),
                () -> assertThat(orderJpaRepository.count()).isZero()
            );
        }

        @DisplayName("quantity=0 라인이 포함되면, INVALID_QUANTITY 예외가 발생하고 orders 행이 생성되지 않는다.")
        @Test
        void throwsInvalidQuantity_whenQuantityIsZero() {
            // given
            OrderCommand.Place command = new OrderCommand.Place(List.of(
                new OrderCommand.Line(productAId, 0)
            ));

            // when
            CoreException exception = assertThrows(CoreException.class,
                () -> orderFacade.placeOrder(userId, command));

            // then
            assertAll(
                () -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_QUANTITY),
                () -> assertThat(orderJpaRepository.count()).isZero()
            );
        }

        @DisplayName("존재하지 않는 productId 가 포함되면, PRODUCT_NOT_FOUND 예외가 발생하고 orders 행이 생성되지 않는다.")
        @Test
        void throwsProductNotFound_whenProductDoesNotExist() {
            // given
            OrderCommand.Place command = new OrderCommand.Place(List.of(
                new OrderCommand.Line(999L, 1)
            ));

            // when
            CoreException exception = assertThrows(CoreException.class,
                () -> orderFacade.placeOrder(userId, command));

            // then
            assertAll(
                () -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.PRODUCT_NOT_FOUND),
                () -> assertThat(orderJpaRepository.count()).isZero()
            );
        }

        @DisplayName("soft-deleted product 가 라인에 포함되면, PRODUCT_NOT_FOUND 예외가 발생하고 orders 행이 생성되지 않는다.")
        @Test
        void throwsProductNotFound_whenProductIsSoftDeleted() {
            // given
            softDeleteProduct(productAId);
            OrderCommand.Place command = new OrderCommand.Place(List.of(
                new OrderCommand.Line(productAId, 1)
            ));

            // when
            CoreException exception = assertThrows(CoreException.class,
                () -> orderFacade.placeOrder(userId, command));

            // then
            assertAll(
                () -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.PRODUCT_NOT_FOUND),
                () -> assertThat(orderJpaRepository.count()).isZero()
            );
        }

        @DisplayName("존재하지 않는 userId 면, USER_NOT_FOUND 예외가 발생하고 orders 행이 생성되지 않는다.")
        @Test
        void throwsUserNotFound_whenUserDoesNotExist() {
            // given
            Long nonExistentUserId = 999L;
            OrderCommand.Place command = new OrderCommand.Place(List.of(
                new OrderCommand.Line(productAId, 1)
            ));

            // when
            CoreException exception = assertThrows(CoreException.class,
                () -> orderFacade.placeOrder(nonExistentUserId, command));

            // then
            assertAll(
                () -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.USER_NOT_FOUND),
                () -> assertThat(orderJpaRepository.count()).isZero()
            );
        }

        @DisplayName("단일 라인의 재고가 부족하면, OUT_OF_STOCK 예외가 발생하고 orders 행이 생성되지 않으며 재고도 변하지 않는다.")
        @Test
        void throwsOutOfStock_whenSingleLineExceedsStock() {
            // given
            OrderCommand.Place command = new OrderCommand.Place(List.of(
                new OrderCommand.Line(productAId, 15)
            ));

            // when
            CoreException exception = assertThrows(CoreException.class,
                () -> orderFacade.placeOrder(userId, command));

            // then
            assertAll(
                () -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.OUT_OF_STOCK),
                () -> assertThat(orderJpaRepository.count()).isZero(),
                () -> assertThat(loadStockQuantity(productAId)).isEqualTo(10)
            );
        }

        @DisplayName("다항목 주문 중 일부 재고만 부족하면, OUT_OF_STOCK 으로 전체가 롤백되어 다른 상품의 재고와 orders 도 모두 변하지 않는다.")
        @Test
        void rollsBackAllChanges_whenPartialStockShortage() {
            // given
            OrderCommand.Place command = new OrderCommand.Place(List.of(
                new OrderCommand.Line(productAId, 5),
                new OrderCommand.Line(productBId, 15)
            ));

            // when
            CoreException exception = assertThrows(CoreException.class,
                () -> orderFacade.placeOrder(userId, command));

            // then
            assertAll(
                () -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.OUT_OF_STOCK),
                () -> assertThat(orderJpaRepository.count()).isZero(),
                () -> assertThat(loadStockQuantity(productAId)).isEqualTo(10),
                () -> assertThat(loadStockQuantity(productBId)).isEqualTo(10)
            );
        }

        @DisplayName("주문 후 상품의 이름/가격이 변경되어도, 기존 OrderItem 의 스냅샷은 변하지 않는다.")
        @Test
        void keepsSnapshotIsolated_whenProductIsUpdatedAfterOrder() {
            // given
            OrderCommand.Place command = new OrderCommand.Place(List.of(
                new OrderCommand.Line(productAId, 1)
            ));
            OrderInfo result = orderFacade.placeOrder(userId, command);

            // when
            productFacade.updateProduct(productAId, "에어맥스 90 (개편)", "신규 컬러", 200_000L, 10);

            // then
            List<OrderItem> savedItems = orderItemJpaRepository.findByOrderId(result.id());
            assertAll(
                () -> assertThat(savedItems).hasSize(1),
                () -> assertThat(savedItems.get(0).getProductName()).isEqualTo("에어맥스 270"),
                () -> assertThat(savedItems.get(0).getProductPrice()).isEqualTo(100_000L)
            );
        }
    }

    private Integer loadStockQuantity(Long productId) {
        return stockJpaRepository.findByProductId(productId).orElseThrow().getQuantity();
    }

    private void softDeleteProduct(Long productId) {
        ProductModel product = productJpaRepository.findById(productId).orElseThrow();
        product.delete();
        productJpaRepository.save(product);
    }
}
