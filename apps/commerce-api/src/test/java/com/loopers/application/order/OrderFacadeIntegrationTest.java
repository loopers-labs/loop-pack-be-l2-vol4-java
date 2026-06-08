package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.order.OrderItemInput;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.enums.OrderStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductStockModel;
import com.loopers.domain.product.ProductStockRepository;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.ProductName;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class OrderFacadeIntegrationTest {

    @Autowired private OrderFacade orderFacade;
    @Autowired private OrderRepository orderRepository;
    @Autowired private ProductStockRepository productStockRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private static final Long USER_ID = 1L;

    private ProductStockModel stock;

    @BeforeEach
    void setUp() {
        BrandModel brand = brandRepository.save(new BrandModel("테스트브랜드"));
        ProductModel product = productRepository.save(new ProductModel(brand.getId(), new ProductName("테스트상품")));
        stock = productStockRepository.save(new ProductStockModel(product, new Price(10000L), 10));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("주문 생성(createOrder) 시,")
    @Nested
    class CreateOrder {

        @DisplayName("주문이 생성되고, 재고가 감소한다.")
        @Test
        void createsOrderAndDecreasesStock() {
            List<OrderItemInput> inputs = List.of(new OrderItemInput(stock.getId(), 3));

            OrderInfo result = orderFacade.createOrder(USER_ID, inputs);

            assertThat(result.totalAmount()).isEqualTo(30000L);
            assertThat(result.items()).hasSize(1);
            assertThat(result.status()).isEqualTo(OrderStatus.REQUESTED.getDescription());

            int remainingStock = productStockRepository.findById(stock.getId()).get().getStockQuantity().getValue();
            assertThat(remainingStock).isEqualTo(7);
        }

        @DisplayName("재고가 부족하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStockIsInsufficient() {
            List<OrderItemInput> inputs = List.of(new OrderItemInput(stock.getId(), 100));

            CoreException exception = assertThrows(CoreException.class,
                    () -> orderFacade.createOrder(USER_ID, inputs));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("존재하지 않는 재고 ID면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenStockDoesNotExist() {
            List<OrderItemInput> inputs = List.of(new OrderItemInput(999L, 1));

            CoreException exception = assertThrows(CoreException.class,
                    () -> orderFacade.createOrder(USER_ID, inputs));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("동일한 stockId가 중복되면, 합산된 수량으로 주문이 생성되고 재고가 감소한다.")
        @Test
        void mergesDuplicateInputsAndDecreasesStock() {
            List<OrderItemInput> inputs = List.of(
                    new OrderItemInput(stock.getId(), 2),
                    new OrderItemInput(stock.getId(), 3)
            );

            OrderInfo result = orderFacade.createOrder(USER_ID, inputs);

            assertThat(result.items()).hasSize(1);
            assertThat(result.totalAmount()).isEqualTo(50000L);

            int remainingStock = productStockRepository.findById(stock.getId()).get().getStockQuantity().getValue();
            assertThat(remainingStock).isEqualTo(5);
        }
    }

    @DisplayName("주문 취소(cancelOrder) 시,")
    @Nested
    class CancelOrder {

        @DisplayName("주문이 취소되고, 재고가 복구된다.")
        @Test
        void cancelsOrderAndRestoresStock() {
            OrderInfo order = orderFacade.createOrder(USER_ID, List.of(new OrderItemInput(stock.getId(), 3)));

            OrderInfo result = orderFacade.cancelOrder(order.id(), USER_ID);

            assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED.getDescription());

            int restoredStock = productStockRepository.findById(stock.getId()).get().getStockQuantity().getValue();
            assertThat(restoredStock).isEqualTo(10);
        }
    }
}
