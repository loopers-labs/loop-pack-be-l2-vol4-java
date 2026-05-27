package com.loopers.domain.stock;

import com.loopers.domain.stock.model.Stock;
import com.loopers.domain.stock.repository.StockRepository;
import com.loopers.domain.stock.service.StockDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StockDomainServiceTest {

    private StockRepository stockRepository;
    private StockDomainService stockDomainService;

    @BeforeEach
    void setUp() {
        stockRepository = mock(StockRepository.class);
        stockDomainService = new StockDomainService(stockRepository);
    }

    @DisplayName("재고를 생성할 때, ")
    @Nested
    class CreateStock {

        @DisplayName("올바른 값이 주어지면, 재고가 저장된다.")
        @Test
        void savesStock_whenInputIsValid() {
            // Arrange
            when(stockRepository.save(any(Stock.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            Stock result = stockDomainService.createStock(1L, 10);

            // Assert
            assertThat(result.getProductId()).isEqualTo(1L);
            assertThat(result.getQuantity()).isEqualTo(10);
        }

        @DisplayName("초기 수량이 0이면, 수량 0으로 저장된다.")
        @Test
        void savesStock_whenQuantityIsZero() {
            // Arrange
            when(stockRepository.save(any(Stock.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            Stock result = stockDomainService.createStock(1L, 0);

            // Assert
            assertThat(result.getQuantity()).isZero();
        }
    }
}
