package com.loopers.domain.stock;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StockServiceTest {

    private StockService stockService;
    private StockRepository stockRepository;

    @BeforeEach
    void setUp() {
        stockRepository = mock(StockRepository.class);
        stockService = new StockService(stockRepository);
    }

    @DisplayName("재고를 productId로 조회할 때,")
    @Nested
    class GetByProductId {

        @DisplayName("존재하는 productId라면 재고가 반환된다.")
        @Test
        void returnsStock_whenStockExists() {
            // given
            Long productId = 1L;
            StockModel stock = new StockModel(productId, 10L);
            when(stockRepository.findByProductId(productId)).thenReturn(Optional.of(stock));

            // when
            StockModel result = stockService.getByProductId(productId);

            // then
            assertThat(result).isSameAs(stock);
        }

        @DisplayName("존재하지 않는 productId라면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenStockDoesNotExist() {
            // given
            Long productId = 999L;
            when(stockRepository.findByProductId(productId)).thenReturn(Optional.empty());

            // when
            CoreException result = assertThrows(CoreException.class, () -> stockService.getByProductId(productId));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("재고를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("정상 입력이라면 저장된 재고가 반환된다.")
        @Test
        void createsStock_whenValidInputIsProvided() {
            // given
            Long productId = 1L;
            StockModel expected = new StockModel(productId, 50L);
            when(stockRepository.save(any(StockModel.class))).thenReturn(expected);

            // when
            StockModel result = stockService.create(productId, 50L);

            // then
            assertThat(result).isSameAs(expected);
        }
    }

    @DisplayName("재고를 수정할 때,")
    @Nested
    class Update {

        @DisplayName("존재하는 재고라면 수정된 재고가 반환된다.")
        @Test
        void updatesStock_whenStockExists() {
            // given
            Long productId = 1L;
            StockModel stock = new StockModel(productId, 10L);
            when(stockRepository.findByProductId(productId)).thenReturn(Optional.of(stock));
            when(stockRepository.save(stock)).thenReturn(stock);

            // when
            StockModel result = stockService.update(productId, 30L);

            // then
            assertThat(result.getQuantity()).isEqualTo(30L);
        }
    }

    @DisplayName("재고를 삭제할 때,")
    @Nested
    class Delete {

        @DisplayName("존재하는 재고라면 예외 없이 완료된다.")
        @Test
        void deletesStock_whenStockExists() {
            // given
            Long productId = 1L;
            StockModel stock = new StockModel(productId, 10L);
            when(stockRepository.findByProductId(productId)).thenReturn(Optional.of(stock));
            when(stockRepository.save(stock)).thenReturn(stock);

            // when & then
            assertDoesNotThrow(() -> stockService.delete(productId));
        }
    }
}
