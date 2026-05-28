package com.loopers.domain.stock;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    private static final Long PRODUCT_ID = 1L;

    @Mock
    private StockRepository stockRepository;

    @InjectMocks
    private StockService stockService;

    @DisplayName("재고 차감 시")
    @Nested
    class Decrease {

        @DisplayName("재고가 존재하고 차감 가능하면 quantity가 감소한다 (FOR UPDATE 경로 사용)")
        @Test
        void decreasesQuantity_whenStockExistsAndSufficient() {
            // given
            StockModel stock = new StockModel(PRODUCT_ID, 10);
            when(stockRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(stock));

            // when
            stockService.decrease(PRODUCT_ID, 3);

            // then
            assertThat(stock.getQuantity()).isEqualTo(7);
        }

        @DisplayName("재고가 없으면 NOT_FOUND 예외가 발생한다")
        @Test
        void throwsNotFound_whenStockDoesNotExist() {
            // given
            when(stockRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(Optional.empty());

            // when
            CoreException ex = assertThrows(CoreException.class, () -> stockService.decrease(PRODUCT_ID, 1));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("재고가 부족하면 CONFLICT 예외가 발생한다")
        @Test
        void throwsConflict_whenStockIsInsufficient() {
            // given
            StockModel stock = new StockModel(PRODUCT_ID, 2);
            when(stockRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(stock));

            // when
            CoreException ex = assertThrows(CoreException.class, () -> stockService.decrease(PRODUCT_ID, 3));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("재고 증가 시")
    @Nested
    class Increase {

        @DisplayName("재고가 존재하면 quantity가 증가한다 (FOR UPDATE 경로 — decrease와 동일 행)")
        @Test
        void increasesQuantity_whenStockExists() {
            // given
            StockModel stock = new StockModel(PRODUCT_ID, 10);
            when(stockRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(stock));

            // when
            stockService.increase(PRODUCT_ID, 5);

            // then
            assertThat(stock.getQuantity()).isEqualTo(15);
        }

        @DisplayName("재고가 없으면 NOT_FOUND 예외가 발생한다")
        @Test
        void throwsNotFound_whenStockDoesNotExist() {
            // given
            when(stockRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(Optional.empty());

            // when
            CoreException ex = assertThrows(CoreException.class, () -> stockService.increase(PRODUCT_ID, 1));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("productId 집합으로 일괄 조회 시")
    @Nested
    class GetQuantities {

        @DisplayName("주어진 productId 들의 quantity를 Map으로 반환한다")
        @Test
        void returnsQuantityMap_forGivenProductIds() {
            // given
            StockModel stock1 = new StockModel(1L, 10);
            StockModel stock2 = new StockModel(2L, 5);
            when(stockRepository.findAllByProductIdIn(List.of(1L, 2L))).thenReturn(List.of(stock1, stock2));

            // when
            Map<Long, Integer> quantities = stockService.getQuantities(List.of(1L, 2L));

            // then
            assertThat(quantities).containsEntry(1L, 10).containsEntry(2L, 5);
        }

        @DisplayName("빈 컬렉션을 전달하면 빈 Map을 반환하고 Repository를 호출하지 않는다")
        @Test
        void returnsEmptyMap_whenInputIsEmpty() {
            // when
            Map<Long, Integer> quantities = stockService.getQuantities(List.of());

            // then
            assertThat(quantities).isEmpty();
        }
    }
}
