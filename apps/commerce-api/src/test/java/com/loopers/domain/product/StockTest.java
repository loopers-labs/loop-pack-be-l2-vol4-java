package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StockTest {

    @DisplayName("Stock 을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("0 이상의 quantity 이면 생성된다.")
        @ParameterizedTest
        @ValueSource(ints = {0, 1, 100, Integer.MAX_VALUE})
        void createsStock_whenNonNegative(int quantity) {
            // act
            Stock stock = Stock.of(quantity);

            // assert
            assertThat(stock.getQuantity()).isEqualTo(quantity);
        }

        @DisplayName("quantity 가 음수면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNegative() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> Stock.of(-1));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("adjust 로 재고를 조정할 때, ")
    @Nested
    class Adjust {

        @DisplayName("0 이상의 newQuantity 이면 새 Stock 으로 교체된다.")
        @ParameterizedTest
        @ValueSource(ints = {0, 5, 100})
        void adjustsToNewQuantity(int newQuantity) {
            // arrange
            Stock stock = Stock.of(10);

            // act
            Stock result = stock.adjust(newQuantity);

            // assert
            assertThat(result.getQuantity()).isEqualTo(newQuantity);
        }

        @DisplayName("newQuantity 가 음수이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNegative() {
            // arrange
            Stock stock = Stock.of(10);

            // act
            CoreException result = assertThrows(CoreException.class, () -> stock.adjust(-1));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("isSoldOut 을 호출할 때, ")
    @Nested
    class IsSoldOut {

        @DisplayName("quantity 가 0 이면 true 를 반환한다.")
        @Test
        void returnsTrue_whenZero() {
            assertThat(Stock.of(0).isSoldOut()).isTrue();
        }

        @DisplayName("quantity 가 0 이 아니면 false 를 반환한다.")
        @Test
        void returnsFalse_whenNotZero() {
            assertThat(Stock.of(1).isSoldOut()).isFalse();
        }
    }
}
