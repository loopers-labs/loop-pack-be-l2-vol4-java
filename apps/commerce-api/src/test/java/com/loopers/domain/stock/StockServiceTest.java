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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock
    private StockRepository stockRepository;

    @InjectMocks
    private StockService stockService;

    @DisplayName("재고를 차감할 때, ")
    @Nested
    class Decrease {

        @DisplayName("정상 amount 가 주어지면, 해당 productId 의 stock 잔량이 amount 만큼 줄어든다.")
        @Test
        void decreasesStockQuantity_whenAmountIsValid() {
            // given
            Long productId = 1L;
            StockModel stock = new StockModel(productId, 10);
            given(stockRepository.findByProductIdForUpdate(productId)).willReturn(Optional.of(stock));

            // when
            stockService.decrease(productId, 3);

            // then
            assertThat(stock.getQuantity()).isEqualTo(7);
        }

        @DisplayName("amount 가 0 이면, INVALID_QUANTITY 예외가 발생하고 stock 조회조차 수행되지 않는다.")
        @Test
        void throwsInvalidQuantityException_whenAmountIsZero() {
            // given
            Long productId = 1L;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> stockService.decrease(productId, 0));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.INVALID_QUANTITY),
                () -> verify(stockRepository, never()).findByProductIdForUpdate(productId)
            );
        }

        @DisplayName("amount 가 음수이면, INVALID_QUANTITY 예외가 발생하고 stock 조회조차 수행되지 않는다.")
        @Test
        void throwsInvalidQuantityException_whenAmountIsNegative() {
            // given
            Long productId = 1L;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> stockService.decrease(productId, -1));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.INVALID_QUANTITY),
                () -> verify(stockRepository, never()).findByProductIdForUpdate(productId)
            );
        }

        @DisplayName("해당 productId 의 stock 이 존재하지 않으면, PRODUCT_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsProductNotFoundException_whenStockDoesNotExist() {
            // given
            Long productId = 1L;
            given(stockRepository.findByProductIdForUpdate(productId)).willReturn(Optional.empty());

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> stockService.decrease(productId, 1));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.PRODUCT_NOT_FOUND);
        }

        @DisplayName("잔량보다 큰 amount 가 주어지면, StockModel.decrease 의 OUT_OF_STOCK 예외가 그대로 전파된다.")
        @Test
        void propagatesOutOfStockException_whenAmountExceedsCurrent() {
            // given
            Long productId = 1L;
            StockModel stock = new StockModel(productId, 2);
            given(stockRepository.findByProductIdForUpdate(productId)).willReturn(Optional.of(stock));

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> stockService.decrease(productId, 5));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.OUT_OF_STOCK),
                () -> assertThat(stock.getQuantity()).isEqualTo(2)
            );
        }
    }
}
