package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class StockTest {

    @Nested
    @DisplayName("Stock 생성")
    class Create {

        @DisplayName("0 이상의 값으로 생성된다 (0 경계 포함)")
        @ParameterizedTest
        @ValueSource(ints = {0, 1, 100})
        void given_nonNegative_when_create_then_creates(int value) {
            Stock stock = new Stock(value);
            assertThat(stock.getQuantity()).isEqualTo(value);
        }

        @DisplayName("null이거나 음수면 BAD_REQUEST 예외가 발생한다")
        @Test
        void given_nullValue_when_create_then_throwsBadRequest() {
            CoreException result = assertThrows(CoreException.class, () -> new Stock(null));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("음수면 BAD_REQUEST 예외가 발생한다")
        @ParameterizedTest
        @ValueSource(ints = {-1, -100})
        void given_negative_when_create_then_throwsBadRequest(int value) {
            CoreException result = assertThrows(CoreException.class, () -> new Stock(value));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("차감 (deduct)")
    class Deduct {

        @DisplayName("충분하면 차감된 새 Stock을 반환하고, 원본은 불변이다")
        @Test
        void given_enough_when_deduct_then_returnsNewStock_andOriginalUnchanged() {
            Stock stock = new Stock(10);

            Stock after = stock.deduct(3);

            assertAll(
                    () -> assertThat(after.getQuantity()).isEqualTo(7),
                    () -> assertThat(stock.getQuantity()).isEqualTo(10)   // 불변
            );
        }

        @DisplayName("재고와 동일 수량을 차감하면 0이 된다 (경계)")
        @Test
        void given_exact_when_deduct_then_zero() {
            assertThat(new Stock(5).deduct(5).getQuantity()).isEqualTo(0);
        }

        @DisplayName("재고보다 많으면 CONFLICT 예외가 발생한다")
        @Test
        void given_insufficient_when_deduct_then_throwsConflict() {
            CoreException result = assertThrows(CoreException.class, () -> new Stock(2).deduct(3));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("차감 수량이 0 이하면 BAD_REQUEST 예외가 발생한다")
        @ParameterizedTest
        @ValueSource(ints = {0, -1})
        void given_nonPositive_when_deduct_then_throwsBadRequest(int amount) {
            CoreException result = assertThrows(CoreException.class, () -> new Stock(10).deduct(amount));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("복원 (restore)")
    class Restore {

        @DisplayName("복원하면 증가된 새 Stock을 반환한다")
        @Test
        void given_quantity_when_restore_then_increased() {
            assertThat(new Stock(5).restore(3).getQuantity()).isEqualTo(8);
        }

        @DisplayName("복원 수량이 0 이하면 BAD_REQUEST 예외가 발생한다")
        @ParameterizedTest
        @ValueSource(ints = {0, -1})
        void given_nonPositive_when_restore_then_throwsBadRequest(int amount) {
            CoreException result = assertThrows(CoreException.class, () -> new Stock(5).restore(amount));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("가용성 (isAvailable)")
    class IsAvailable {

        @DisplayName("재고가 요청 수량 이상이면 true, 미만이면 false")
        @Test
        void given_stock_when_isAvailable_then_comparesQuantity() {
            Stock stock = new Stock(5);

            assertAll(
                    () -> assertThat(stock.isAvailable(5)).isTrue(),
                    () -> assertThat(stock.isAvailable(3)).isTrue(),
                    () -> assertThat(stock.isAvailable(6)).isFalse()
            );
        }
    }

    @Nested
    @DisplayName("동등성")
    class Equality {

        @DisplayName("같은 수량이면 equals/hashCode가 동일하다")
        @Test
        void given_sameQuantity_when_equals_then_equal() {
            assertAll(
                    () -> assertThat(new Stock(7)).isEqualTo(new Stock(7)),
                    () -> assertThat(new Stock(7)).hasSameHashCodeAs(new Stock(7)),
                    () -> assertThat(new Stock(7)).isNotEqualTo(new Stock(8))
            );
        }
    }
}
