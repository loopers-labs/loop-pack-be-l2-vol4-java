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

}
