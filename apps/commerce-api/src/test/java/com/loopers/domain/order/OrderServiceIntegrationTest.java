package com.loopers.domain.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.stock.StockService;
import com.loopers.fixture.BrandFixture;
import com.loopers.fixture.OrderFixture;
import com.loopers.fixture.ProductFixture;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class OrderServiceIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private BrandService brandService;

    @Autowired
    private ProductService productService;

    @Autowired
    private StockService stockService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private UUID userId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        BrandModel brand = brandService.create(BrandFixture.NAME, BrandFixture.DESCRIPTION);
        ProductModel product = productService.create(brand, ProductFixture.NAME, ProductFixture.DESCRIPTION, ProductFixture.PRICE);
        stockService.create(product.getId(), ProductFixture.INITIAL_QUANTITY);
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private OrderModel createOrder(UUID userId) {
        return orderService.create(userId, OrderFixture.RECEIVER_NAME, OrderFixture.RECEIVER_PHONE,
            OrderFixture.ZIP_CODE, OrderFixture.ADDRESS, OrderFixture.DETAIL_ADDRESS);
    }

    @DisplayName("주문을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 값이면, PENDING 상태 주문이 저장된다.")
        @Test
        void returnsSavedOrder_whenValid() {
            // arrange & act
            OrderModel order = createOrder(userId);

            // assert
            assertAll(
                () -> assertThat(order.getId()).isNotNull(),
                () -> assertThat(order.getUserId()).isEqualTo(userId),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING),
                () -> assertThat(order.getPgAmount()).isEqualTo(0L)
            );
        }
    }

    @DisplayName("주문 아이템을 추가할 때,")
    @Nested
    class AddItem {

        @DisplayName("아이템 추가 시, pgAmount가 증가한다.")
        @Test
        void increasesPgAmount_whenItemAdded() {
            // arrange
            OrderModel order = createOrder(userId);
            OrderItemModel item = OrderFixture.createItem(productId);

            // act
            orderService.addItem(order, item);

            // assert
            assertAll(
                () -> assertThat(order.getItems()).hasSize(1),
                () -> assertThat(order.getPgAmount()).isEqualTo(ProductFixture.PRICE * 2)
            );
        }
    }

    @DisplayName("주문을 단건 조회할 때,")
    @Nested
    class Get {

        @DisplayName("존재하지 않는 ID면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenIdNotExists() {
            CoreException ex = assertThrows(CoreException.class, () ->
                orderService.get(UUID.randomUUID())
            );
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("존재하는 주문이면, 반환된다.")
        @Test
        void returnsOrder_whenExists() {
            // arrange
            OrderModel saved = createOrder(userId);

            // act
            OrderModel found = orderService.get(saved.getId());

            // assert
            assertThat(found.getId()).isEqualTo(saved.getId());
        }
    }

    @DisplayName("주문을 확정할 때,")
    @Nested
    class Confirm {

        @DisplayName("PENDING 상태이고 금액이 일치하면, CONFIRMED 로 변경된다.")
        @Test
        void confirmsOrder_whenPendingAndAmountMatches() {
            // arrange
            OrderModel order = createOrder(userId);

            // act
            orderService.confirm(order, 0L); // 아이템 없으므로 pgAmount = 0

            // assert
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        }
    }

    @DisplayName("주문을 실패 처리할 때,")
    @Nested
    class Fail {

        @DisplayName("PENDING 상태면, FAILED 로 변경된다.")
        @Test
        void failsOrder_whenPending() {
            // arrange
            OrderModel order = createOrder(userId);

            // act
            orderService.fail(order);

            // assert
            assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        }
    }

    @DisplayName("주문을 취소할 때,")
    @Nested
    class Cancel {

        @DisplayName("CONFIRMED 상태면, CANCELLED 로 변경된다.")
        @Test
        void cancelsOrder_whenConfirmed() {
            // arrange
            OrderModel order = createOrder(userId);
            orderService.confirm(order, 0L);

            // act
            orderService.cancel(order);

            // assert
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }
    }
}
