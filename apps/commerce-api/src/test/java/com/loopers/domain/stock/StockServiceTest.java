package com.loopers.domain.stock;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * StockService 순수 단위 테스트 — Repository를 mock으로 격리해 DB 없이
 * 차감(비관/낙관 경로 선택)·복원·조회 흐름과 부재/부족 예외를 검증한다.
 * (실제 동시성·락 정합성은 StockServiceIntegrationTest가 Testcontainers로 검증)
 */
class StockServiceTest {

    private static final Long PRODUCT_ID = 10L;

    private StockRepository stockRepository;
    private StockService stockService;

    @BeforeEach
    void setUp() {
        stockRepository = mock(StockRepository.class);
        stockService = new StockService(stockRepository);
    }

    @Nested
    @DisplayName("재고 차감 (decrease — 비관 경로)")
    class Decrease {

        @DisplayName("비관 락 조회로 행을 잠그고 차감 후 저장한다.")
        @Test
        void given_enough_when_decrease_then_locksAndSaves() {
            StockModel stock = StockModel.reconstitute(1L, PRODUCT_ID, 10);
            when(stockRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(stock));

            stockService.decrease(PRODUCT_ID, 3);

            assertThat(stock.getQuantity()).isEqualTo(7);
            verify(stockRepository).findByProductIdForUpdate(PRODUCT_ID);
            verify(stockRepository).save(stock);
            verify(stockRepository, never()).findByProductId(anyLong());
        }

        @DisplayName("재고 행이 없으면 NOT_FOUND 예외가 발생하고 저장하지 않는다.")
        @Test
        void given_noStock_when_decrease_then_throwsNotFound() {
            when(stockRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> stockService.decrease(PRODUCT_ID, 3));

            assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verify(stockRepository, never()).save(any());
        }

        @DisplayName("재고가 부족하면 CONFLICT 예외가 발생하고 저장하지 않는다.")
        @Test
        void given_insufficient_when_decrease_then_throwsConflict() {
            StockModel stock = StockModel.reconstitute(1L, PRODUCT_ID, 2);
            when(stockRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(stock));

            Throwable thrown = catchThrowable(() -> stockService.decrease(PRODUCT_ID, 3));

            assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.CONFLICT);
            verify(stockRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("재고 차감 (decreaseOptimistic — 낙관 경로)")
    class DecreaseOptimistic {

        @DisplayName("일반 조회로 차감 후 저장한다 (@Version이 동시성 보장).")
        @Test
        void given_enough_when_decreaseOptimistic_then_savesViaPlainFind() {
            StockModel stock = StockModel.reconstitute(1L, PRODUCT_ID, 10);
            when(stockRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(stock));

            stockService.decreaseOptimistic(PRODUCT_ID, 4);

            assertThat(stock.getQuantity()).isEqualTo(6);
            verify(stockRepository).findByProductId(PRODUCT_ID);
            verify(stockRepository).save(stock);
            verify(stockRepository, never()).findByProductIdForUpdate(anyLong());
        }
    }

    @Nested
    @DisplayName("재고 복원 (increase)")
    class Increase {

        @DisplayName("비관 락 조회로 행을 잠그고 복원 후 저장한다.")
        @Test
        void given_stock_when_increase_then_locksAndSaves() {
            StockModel stock = StockModel.reconstitute(1L, PRODUCT_ID, 5);
            when(stockRepository.findByProductIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(stock));

            stockService.increase(PRODUCT_ID, 3);

            assertThat(stock.getQuantity()).isEqualTo(8);
            verify(stockRepository).save(stock);
        }
    }

    @Nested
    @DisplayName("재고 조회")
    class Query {

        @DisplayName("단건 조회 — 재고 행이 없으면 0을 반환한다.")
        @Test
        void given_noStock_when_getQuantity_then_zero() {
            when(stockRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.empty());
            assertThat(stockService.getQuantity(PRODUCT_ID)).isEqualTo(0);
        }

        @DisplayName("batch 조회 — productId→수량 Map으로 반환한다.")
        @Test
        void given_productIds_when_findQuantities_then_returnsMap() {
            when(stockRepository.findByProductIds(List.of(10L, 20L))).thenReturn(List.of(
                    StockModel.reconstitute(1L, 10L, 5),
                    StockModel.reconstitute(2L, 20L, 0)
            ));

            Map<Long, Integer> result = stockService.findQuantities(List.of(10L, 20L));

            assertThat(result).containsEntry(10L, 5).containsEntry(20L, 0);
        }

        @DisplayName("batch 조회 — 빈 입력이면 빈 Map (조회하지 않는다).")
        @Test
        void given_empty_when_findQuantities_then_emptyMap() {
            assertThat(stockService.findQuantities(List.of())).isEmpty();
            verify(stockRepository, never()).findByProductIds(any());
        }
    }
}
