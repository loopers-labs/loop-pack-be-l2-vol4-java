package com.loopers.domain.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.stock.StockModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderDomainServiceTest {

    // OrderPricingService는 순수 계산 로직이므로 실제 구현체를 함께 사용
    private final OrderPricingService orderPricingService = new OrderPricingService();
    private final OrderDomainService orderDomainService = new OrderDomainService(orderPricingService);

    private ProductModel product;
    private StockModel stock;

    @BeforeEach
    void setUp() {
        BrandModel brand = new BrandModel("Nike", "스포츠 브랜드");
        product = new ProductModel(brand, "나이키 에어맥스", 150_000);
        ReflectionTestUtils.setField(product, "id", 10L);
        stock = new StockModel(product, 100);
    }

    @DisplayName("validateStocks()를 호출할 때,")
    @Nested
    class ValidateStocks {

        @DisplayName("재고가 충분한 경우 검증을 통과한다.")
        @Test
        void doesNotThrow_whenStockIsSufficient() {
            // arrange
            Map<Long, StockModel> stockMap = Map.of(10L, stock);
            Map<Long, Integer> quantityMap = Map.of(10L, 5);

            // act & assert
            assertDoesNotThrow(() -> orderDomainService.validateStocks(stockMap, quantityMap));
        }

        @DisplayName("재고와 수량이 정확히 같으면 검증을 통과한다.")
        @Test
        void doesNotThrow_whenStockEqualsQuantity() {
            // arrange
            Map<Long, StockModel> stockMap = Map.of(10L, stock);
            Map<Long, Integer> quantityMap = Map.of(10L, 100); // 재고 = 요청량

            // act & assert
            assertDoesNotThrow(() -> orderDomainService.validateStocks(stockMap, quantityMap));
        }

        @DisplayName("재고가 부족한 경우 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenStockIsInsufficient() {
            // arrange
            Map<Long, StockModel> stockMap = Map.of(10L, stock);
            Map<Long, Integer> quantityMap = Map.of(10L, 101); // 재고(100) < 요청(101)

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> orderDomainService.validateStocks(stockMap, quantityMap)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("buildOrder()를 호출할 때,")
    @Nested
    class BuildOrder {

        @DisplayName("상품 스냅샷이 OrderItem에 담기고 totalAmount가 정확히 계산된다.")
        @Test
        void buildsOrderWithCorrectSnapshot_whenValidInputProvided() {
            // arrange
            Map<Long, Integer> quantityMap = Map.of(10L, 3);

            // act
            OrderModel order = orderDomainService.buildOrder(1L, List.of(product), quantityMap);

            // assert
            assertThat(order.getUserId()).isEqualTo(1L);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(order.getItems()).hasSize(1);
            assertThat(order.getTotalAmount()).isEqualTo(450_000); // 150_000 * 3

            OrderItemModel item = order.getItems().get(0);
            assertThat(item.getProductName()).isEqualTo("나이키 에어맥스");
            assertThat(item.getUnitPrice()).isEqualTo(150_000);
            assertThat(item.getBrandName()).isEqualTo("Nike");
            assertThat(item.getQuantity()).isEqualTo(3);
        }

        @DisplayName("여러 상품 주문 시 totalAmount가 모든 항목의 합산이다.")
        @Test
        void accumulatesTotalAmount_whenMultipleProductsOrdered() {
            // arrange
            BrandModel brand2 = new BrandModel("Adidas", "독일");
            ProductModel product2 = new ProductModel(brand2, "아디다스 삼바", 120_000);
            ReflectionTestUtils.setField(product2, "id", 20L);

            Map<Long, Integer> quantityMap = Map.of(10L, 1, 20L, 2);

            // act
            OrderModel order = orderDomainService.buildOrder(1L, List.of(product, product2), quantityMap);

            // assert
            assertThat(order.getItems()).hasSize(2);
            assertThat(order.getTotalAmount()).isEqualTo(390_000); // 150_000 + 240_000
        }
    }
}
