package com.loopers.domain.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductStockModel;
import com.loopers.domain.product.ProductStockRepository;
import com.loopers.domain.product.ProductStockService;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.ProductName;
import com.loopers.domain.order.enums.OrderStatus;
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
class OrderServiceIntegrationTest {

    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private ProductStockService productStockService;
    @Autowired private ProductStockRepository productStockRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

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

    private OrderModel placeOrder(int quantity) {
        List<OrderItemInput> inputs = List.of(new OrderItemInput(stock.getId(), quantity));
        List<OrderItemInput> mergedItems = orderService.mergeItems(inputs);
        List<ProductStockModel> stocks = productStockService.decrease(mergedItems);
        return orderService.placeOrder(new OrderModel(1L), stocks, mergedItems);
    }

    @DisplayName("주문 생성 시,")
    @Nested
    class PlaceOrder {

        @DisplayName("주문 아이템과 합계가 올바르게 저장된다.")
        @Test
        void savesOrderWithItemsAndTotal() {
            OrderModel saved = placeOrder(2);

            OrderModel found = orderRepository.findById(saved.getId()).get();
            assertThat(found.getItems()).hasSize(1);
            assertThat(found.getTotalMoney().getValue()).isEqualTo(20000L);
            assertThat(found.getStatus()).isEqualTo(OrderStatus.REQUESTED);
        }

        @DisplayName("동일 stockId 입력이 합산되어 아이템 1개로 저장된다.")
        @Test
        void mergesDuplicateStockIds_intoSingleItem() {
            List<OrderItemInput> inputs = List.of(
                    new OrderItemInput(stock.getId(), 2),
                    new OrderItemInput(stock.getId(), 3)
            );
            List<OrderItemInput> mergedItems = orderService.mergeItems(inputs);
            List<ProductStockModel> stocks = productStockService.decrease(mergedItems);
            OrderModel saved = orderService.placeOrder(new OrderModel(1L), stocks, mergedItems);

            OrderModel found = orderRepository.findById(saved.getId()).get();
            assertThat(found.getItems()).hasSize(1);
            assertThat(found.getTotalMoney().getValue()).isEqualTo(50000L);
        }
    }

    @DisplayName("주문 완료 처리 시,")
    @Nested
    class Complete {

        @DisplayName("주문 요청 상태면, 완료 상태로 변경된다.")
        @Test
        void completesOrder_whenStatusIsRequested() {
            OrderModel order = placeOrder(1);

            orderService.complete(order.getId());

            OrderModel updated = orderRepository.findById(order.getId()).get();
            assertThat(updated.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        }
    }

    @DisplayName("주문 취소 처리 시,")
    @Nested
    class Cancel {

        @DisplayName("주문 요청 상태면, 취소 상태로 변경된다.")
        @Test
        void cancelsOrder_whenStatusIsRequested() {
            OrderModel order = placeOrder(1);

            orderService.cancel(order.getId());

            OrderModel updated = orderRepository.findById(order.getId()).get();
            assertThat(updated.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @DisplayName("이미 완료된 주문은 취소할 수 없다.")
        @Test
        void throwsBadRequest_whenOrderIsAlreadyCompleted() {
            OrderModel order = placeOrder(1);
            orderService.complete(order.getId());

            CoreException exception = assertThrows(CoreException.class,
                    () -> orderService.cancel(order.getId()));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
