package com.loopers.domain.order;

import com.loopers.domain.order.enums.OrderStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductStockModel;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.ProductName;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderServiceUnitTest {

    private InMemoryOrderRepository orderRepository;
    private OrderService sut;

    @BeforeEach
    void setUp() {
        orderRepository = new InMemoryOrderRepository();
        sut = new OrderService(orderRepository, new DefaultOrderTotalPolicy());
    }

    private ProductStockModel createStock(String name, long price, int quantity) {
        ProductModel product = new ProductModel(1L, new ProductName(name));
        return new ProductStockModel(product, new Price(price), quantity);
    }

    @DisplayName("주문 생성(placeOrder) 시,")
    @Nested
    class PlaceOrder {

        @DisplayName("주문 아이템에 상품명, 가격이 스냅샷으로 저장된다.")
        @Test
        void snapshotsProductInfo_whenOrderIsPlaced() {
            ProductStockModel stock = createStock("테스트상품", 15000L, 10);
            List<OrderItemInput> inputs = List.of(new OrderItemInput(stock.getId(), 2));
            List<ProductStockModel> stocks = List.of(stock);
            OrderModel order = new OrderModel(1L);

            sut.placeOrder(order, stocks, inputs);

            assertThat(order.getItems()).hasSize(1);
            assertThat(order.getItems().get(0).getProductName()).isEqualTo("테스트상품");
            assertThat(order.getItems().get(0).getProductPrice().getValue()).isEqualTo(15000L);
            assertThat(order.getItems().get(0).getQuantity().getValue()).isEqualTo(2);
        }

        @DisplayName("총 금액은 (가격 × 수량)의 합산이다.")
        @Test
        void calculatesTotalCorrectly_whenOrderIsPlaced() {
            ProductStockModel stock = createStock("테스트상품", 10000L, 10);
            List<OrderItemInput> inputs = List.of(new OrderItemInput(stock.getId(), 3));
            List<ProductStockModel> stocks = List.of(stock);
            OrderModel order = new OrderModel(1L);

            sut.placeOrder(order, stocks, inputs);

            assertThat(order.getTotalMoney().getValue()).isEqualTo(30000L);
        }

        @DisplayName("주문 초기 상태는 REQUESTED이다.")
        @Test
        void setsStatusToRequested_whenOrderIsPlaced() {
            ProductStockModel stock = createStock("테스트상품", 10000L, 10);
            List<OrderItemInput> inputs = List.of(new OrderItemInput(stock.getId(), 1));
            OrderModel order = new OrderModel(1L);

            sut.placeOrder(order, List.of(stock), inputs);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.REQUESTED);
        }
    }

    @DisplayName("주문 완료 처리 시,")
    @Nested
    class Complete {

        @DisplayName("주문이 존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenOrderDoesNotExist() {
            CoreException exception = assertThrows(CoreException.class,
                    () -> sut.complete(999L));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("주문 취소 처리 시,")
    @Nested
    class Cancel {

        @DisplayName("주문이 존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenOrderDoesNotExist() {
            CoreException exception = assertThrows(CoreException.class,
                    () -> sut.cancel(999L));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
