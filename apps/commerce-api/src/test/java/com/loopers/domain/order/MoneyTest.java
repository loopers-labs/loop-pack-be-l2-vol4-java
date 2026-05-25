package com.loopers.domain.order;

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

public class MoneyTest {

    @Nested
    @DisplayName("Money 생성")
    class Create {

        @DisplayName("0 이상의 값으로 생성된다 (0 경계 포함)")
        @ParameterizedTest
        @ValueSource(longs = {0L, 1L, 139000L})
        void given_nonNegative_when_create_then_creates(long value) {
            assertThat(new Money(value).getAmount()).isEqualTo(value);
        }

        @DisplayName("zero()는 0원이다")
        @Test
        void zero_is_zero() {
            assertThat(Money.zero().getAmount()).isEqualTo(0L);
        }

        @DisplayName("null이거나 음수면 BAD_REQUEST 예외가 발생한다")
        @Test
        void given_nullValue_when_create_then_throwsBadRequest() {
            CoreException result = assertThrows(CoreException.class, () -> new Money(null));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("음수면 BAD_REQUEST 예외가 발생한다")
        @ParameterizedTest
        @ValueSource(longs = {-1L, -1000L})
        void given_negative_when_create_then_throwsBadRequest(long value) {
            CoreException result = assertThrows(CoreException.class, () -> new Money(value));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("연산")
    class Operations {

        @DisplayName("add는 두 금액을 더한 새 Money를 반환한다")
        @Test
        void add() {
            assertThat(new Money(1000L).add(new Money(500L))).isEqualTo(new Money(1500L));
        }

        @DisplayName("multiply는 수량을 곱한 새 Money를 반환한다 (lineTotal 계산)")
        @Test
        void multiply() {
            assertThat(new Money(1000L).multiply(3)).isEqualTo(new Money(3000L));
        }

        @DisplayName("multiply의 수량이 음수면 BAD_REQUEST 예외가 발생한다")
        @Test
        void given_negativeFactor_when_multiply_then_throwsBadRequest() {
            CoreException result = assertThrows(CoreException.class, () -> new Money(1000L).multiply(-1));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("동등성")
    class Equality {

        @DisplayName("같은 금액이면 equals/hashCode가 동일하다")
        @Test
        void equality() {
            assertAll(
                    () -> assertThat(new Money(7000L)).isEqualTo(new Money(7000L)),
                    () -> assertThat(new Money(7000L)).hasSameHashCodeAs(new Money(7000L)),
                    () -> assertThat(new Money(7000L)).isNotEqualTo(new Money(8000L))
            );
        }
    }
}
