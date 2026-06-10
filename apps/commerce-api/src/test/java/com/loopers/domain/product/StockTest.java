package com.loopers.domain.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class StockTest {

    @DisplayName("Stock을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("0이면 입력값을 그대로 보존한 Stock이 생성된다.")
        @Test
        void createsStock_whenValueIsMin() {
            // arrange
            Integer value = 0;

            // act
            Stock stock = Stock.from(value);

            // assert
            assertThat(stock.value()).isEqualTo(value);
        }

        @DisplayName("양수면 입력값을 그대로 보존한 Stock이 생성된다.")
        @Test
        void createsStock_whenValueIsPositive() {
            // arrange
            Integer value = 100;

            // act
            Stock stock = Stock.from(value);

            // assert
            assertThat(stock.value()).isEqualTo(value);
        }

        @DisplayName("0 미만이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsNegative() {
            // arrange
            Integer value = -1;

            // act & assert
            assertThatThrownBy(() -> Stock.from(value))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValueIsNull() {
            // arrange & act & assert
            assertThatThrownBy(() -> Stock.from(null))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("재고를 차감할 때,")
    @Nested
    class Decrease {

        @DisplayName("차감 수량이 재고보다 적으면 그만큼 줄어든 Stock이 반환된다.")
        @Test
        void returnsDecreasedStock_whenQuantityIsLessThanStock() {
            // arrange
            Stock stock = Stock.from(10);

            // act
            Stock decreased = stock.decrease(4);

            // assert
            assertThat(decreased.value()).isEqualTo(6);
        }

        @DisplayName("차감 수량이 재고와 같으면 0인 Stock이 반환된다.")
        @Test
        void returnsZeroStock_whenQuantityEqualsStock() {
            // arrange
            Stock stock = Stock.from(5);

            // act
            Stock decreased = stock.decrease(5);

            // assert
            assertThat(decreased.value()).isEqualTo(0);
        }

        @DisplayName("차감 수량이 재고보다 많으면 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenQuantityExceedsStock() {
            // arrange
            Stock stock = Stock.from(3);

            // act & assert
            assertThatThrownBy(() -> stock.decrease(5))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.CONFLICT);
        }
    }
}
