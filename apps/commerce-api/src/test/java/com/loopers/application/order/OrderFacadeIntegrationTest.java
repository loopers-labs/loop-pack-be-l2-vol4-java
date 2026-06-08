package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.stock.StockModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class OrderFacadeIntegrationTest {

    @Autowired private OrderFacade orderFacade;
    @Autowired private BrandJpaRepository brandJpaRepository;
    @Autowired private ProductJpaRepository productJpaRepository;
    @Autowired private StockJpaRepository stockJpaRepository;
    @Autowired private OrderJpaRepository orderJpaRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private static final Long USER_ID = 1L;

    private ProductModel savedProduct;

    @BeforeEach
    void setUp() {
        BrandModel brand = brandJpaRepository.save(new BrandModel("Nike", "스포츠 브랜드"));
        savedProduct = productJpaRepository.save(new ProductModel(brand, "나이키 에어맥스", 150_000));
        stockJpaRepository.save(new StockModel(savedProduct, 10));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private OrderCreateCommand commandWith(int quantity) {
        return new OrderCreateCommand(USER_ID,
            List.of(new OrderItemCommand(savedProduct.getId(), quantity)));
    }

    @DisplayName("createOrder()를 호출할 때,")
    @Nested
    class CreateOrder {

        @DisplayName("유효한 주문 생성 시 DB에 저장되고 OrderInfo가 반환된다.")
        @Test
        void savesOrderAndReturnsInfo_whenValidCommandProvided() {
            OrderInfo result = orderFacade.createOrder(commandWith(2));

            assertAll(
                () -> assertThat(result.id()).isNotNull(),
                () -> assertThat(result.userId()).isEqualTo(USER_ID),
                () -> assertThat(result.status()).isEqualTo(OrderStatus.PENDING),
                () -> assertThat(result.totalAmount()).isEqualTo(300_000),
                () -> assertThat(result.items()).hasSize(1),
                () -> assertThat(orderJpaRepository.findById(result.id())).isPresent()
            );
        }

        @DisplayName("주문 후 재고가 차감된다.")
        @Test
        void decreasesStock_afterOrderCreated() {
            orderFacade.createOrder(commandWith(3));

            int remaining = stockJpaRepository.findByProduct_Id(savedProduct.getId())
                .orElseThrow().getQuantity();
            assertThat(remaining).isEqualTo(7);
        }

        @DisplayName("재고를 초과하는 수량으로 주문 시 BAD_REQUEST 예외가 발생하고 재고가 변하지 않는다.")
        @Test
        void throwsBadRequest_andStockUnchanged_whenQuantityExceedsStock() {
            CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.createOrder(commandWith(999))
            );

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(stockJpaRepository.findByProduct_Id(savedProduct.getId())
                .orElseThrow().getQuantity()).isEqualTo(10);
        }

        @DisplayName("존재하지 않는 상품으로 주문 시 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            OrderCreateCommand command = new OrderCreateCommand(USER_ID,
                List.of(new OrderItemCommand(999L, 1)));

            CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.createOrder(command)
            );

            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("주문 항목이 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenItemsIsEmpty() {
            OrderCreateCommand command = new OrderCreateCommand(USER_ID, List.of());

            CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.createOrder(command)
            );

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("getMyOrders()를 호출할 때,")
    @Nested
    class GetMyOrders {

        @DisplayName("본인 주문 목록이 페이지로 반환된다.")
        @Test
        void returnsMyOrders_whenOrdersExist() {
            orderFacade.createOrder(commandWith(1));
            orderFacade.createOrder(commandWith(2));

            Page<OrderInfo> result = orderFacade.getMyOrders(USER_ID, PageRequest.of(0, 20));

            assertThat(result.getTotalElements()).isEqualTo(2);
        }

        @DisplayName("타인의 주문은 포함되지 않는다.")
        @Test
        void excludesOtherUsersOrders() {
            orderFacade.createOrder(commandWith(1));
            OrderCreateCommand otherCommand = new OrderCreateCommand(99L,
                List.of(new OrderItemCommand(savedProduct.getId(), 1)));
            orderFacade.createOrder(otherCommand);

            Page<OrderInfo> result = orderFacade.getMyOrders(USER_ID, PageRequest.of(0, 20));

            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }

    @DisplayName("getOrder()를 호출할 때,")
    @Nested
    class GetOrder {

        @DisplayName("본인 주문 상세 조회 시 OrderInfo가 반환된다.")
        @Test
        void returnsOrderInfo_whenOrderBelongsToUser() {
            OrderInfo created = orderFacade.createOrder(commandWith(1));

            OrderInfo result = orderFacade.getOrder(USER_ID, created.id());

            assertAll(
                () -> assertThat(result.id()).isEqualTo(created.id()),
                () -> assertThat(result.userId()).isEqualTo(USER_ID),
                () -> assertThat(result.items()).hasSize(1)
            );
        }

        @DisplayName("타인의 주문 조회 시 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenOrderBelongsToOtherUser() {
            OrderInfo created = orderFacade.createOrder(commandWith(1));

            CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.getOrder(99L, created.id())
            );

            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("존재하지 않는 주문 조회 시 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenOrderDoesNotExist() {
            CoreException result = assertThrows(CoreException.class,
                () -> orderFacade.getOrder(USER_ID, 999L)
            );

            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
