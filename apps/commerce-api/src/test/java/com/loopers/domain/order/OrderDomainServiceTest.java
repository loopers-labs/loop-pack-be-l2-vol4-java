package com.loopers.domain.order;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.stock.StockModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderDomainServiceTest {

    private OrderDomainService orderDomainService;

    @BeforeEach
    void setUp() {
        orderDomainService = new OrderDomainService();
    }

    @DisplayName("재고 가용 여부를 검증할 때,")
    @Nested
    class ValidateStockAvailability {

        @DisplayName("모든 상품의 재고가 충분하면, 예외 없이 통과한다.")
        @Test
        void passes_whenAllStocksAreSufficient() {
            // arrange
            StockModel stock1 = new StockModel(1L, 10);
            StockModel stock2 = new StockModel(2L, 5);
            Map<Long, Integer> quantityMap = Map.of(1L, 3, 2L, 5);

            // act & assert
            orderDomainService.validateStockAvailability(List.of(stock1, stock2), quantityMap);
        }

        @DisplayName("재고가 부족한 상품이 있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenAnyStockIsInsufficient() {
            // arrange
            StockModel stock1 = new StockModel(1L, 10);
            StockModel stock2 = new StockModel(2L, 2);
            Map<Long, Integer> quantityMap = Map.of(1L, 3, 2L, 5);

            // act & assert
            assertThatThrownBy(() -> orderDomainService.validateStockAvailability(List.of(stock1, stock2), quantityMap))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("총 가격을 계산할 때,")
    @Nested
    class CalculateTotalPrice {

        @DisplayName("단일 상품의 가격 * 수량을 정확히 계산한다.")
        @Test
        void calculatesSingleProductTotal() {
            // arrange
            ProductModel product = new ProductModel("에어포스1", 10000L, 1L);
            setId(product, 1L);
            Map<Long, Integer> quantityMap = Map.of(1L, 3);

            // act
            long total = orderDomainService.calculateTotalPrice(List.of(product), quantityMap);

            // assert
            assertThat(total).isEqualTo(30000L);
        }

        @DisplayName("여러 상품의 가격 합산이 정확히 계산된다.")
        @Test
        void calculatesMultipleProductTotal() {
            // arrange
            ProductModel product1 = new ProductModel("상품A", 10000L, 1L);
            ProductModel product2 = new ProductModel("상품B", 20000L, 1L);
            setId(product1, 1L);
            setId(product2, 2L);
            Map<Long, Integer> quantityMap = Map.of(1L, 2, 2L, 3);

            // act
            long total = orderDomainService.calculateTotalPrice(List.of(product1, product2), quantityMap);

            // assert
            assertThat(total).isEqualTo(80000L); // 10000*2 + 20000*3
        }
    }

    private void setId(Object entity, long id) {
        try {
            var field = com.loopers.domain.BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
