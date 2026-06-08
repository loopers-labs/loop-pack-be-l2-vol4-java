package com.loopers.domain.product;

import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.ProductName;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductStockServiceUnitTest {

    private InMemoryProductStockRepository stockRepository;
    private ProductStockService sut;

    private static final Long STOCK_ID = 0L;
    private static final Long NON_EXISTENT_ID = 999L;

    @BeforeEach
    void setUp() {
        stockRepository = new InMemoryProductStockRepository();
        sut = new ProductStockService(stockRepository);
    }

    private ProductStockModel saveDefaultStock(int quantity) {
        ProductModel product = new ProductModel(1L, new ProductName("테스트상품"));
        ProductStockModel stock = new ProductStockModel(product, new Price(10000L), quantity);
        return stockRepository.save(stock);
    }

    @DisplayName("재고 단건 조회 시,")
    @Nested
    class Get {

        @DisplayName("재고가 존재하면, 재고 정보를 반환한다.")
        @Test
        void returnsStock_whenStockExists() {
            saveDefaultStock(10);

            ProductStockModel result = sut.get(STOCK_ID);

            assertThat(result.getStockQuantity().getValue()).isEqualTo(10);
        }

        @DisplayName("재고가 존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenStockDoesNotExist() {
            CoreException exception = assertThrows(CoreException.class,
                    () -> sut.get(NON_EXISTENT_ID));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("재고 정보 수정 시,")
    @Nested
    class UpdateStock {

        @DisplayName("가격만 입력하면, 가격만 변경된다.")
        @Test
        void updatesPrice_whenOnlyPriceIsProvided() {
            saveDefaultStock(10);

            ProductStockModel result = sut.updateStock(STOCK_ID, 20000L, null);

            assertThat(result.getPrice().getValue()).isEqualTo(20000L);
            assertThat(result.getStockQuantity().getValue()).isEqualTo(10);
        }

        @DisplayName("양수 재고 증감량 입력하면, 재고가 증가한다.")
        @Test
        void increasesStock_whenStockQuantityIsPositive() {
            saveDefaultStock(10);

            ProductStockModel result = sut.updateStock(STOCK_ID, null, 5);

            assertThat(result.getStockQuantity().getValue()).isEqualTo(15);
        }

        @DisplayName("음수 재고 증감량 입력하면, 재고가 차감된다.")
        @Test
        void decreasesStock_whenStockQuantityIsNegative() {
            saveDefaultStock(10);

            ProductStockModel result = sut.updateStock(STOCK_ID, null, -3);

            assertThat(result.getStockQuantity().getValue()).isEqualTo(7);
        }

        @DisplayName("가격과 재고 증감량 모두 입력하면, 둘 다 변경된다.")
        @Test
        void updatesBoth_whenBothAreProvided() {
            saveDefaultStock(10);

            ProductStockModel result = sut.updateStock(STOCK_ID, 20000L, 5);

            assertThat(result.getPrice().getValue()).isEqualTo(20000L);
            assertThat(result.getStockQuantity().getValue()).isEqualTo(15);
        }

        @DisplayName("재고가 존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenStockDoesNotExist() {
            CoreException exception = assertThrows(CoreException.class,
                    () -> sut.updateStock(NON_EXISTENT_ID, 20000L, null));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
