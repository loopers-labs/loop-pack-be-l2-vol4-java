package com.loopers.domain.vo;

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

class MoneyTest {

    @DisplayName("Money 를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("0 이상의 금액이면, Money 가 정상적으로 생성된다.")
        @Test
        void createsMoney_whenAmountIsNonNegative() {
            // act
            Money money = Money.of(1000L);

            // assert
            assertThat(money.getAmount()).isEqualTo(1000L);
        }

        @DisplayName("금액이 0 이면, Money 가 정상적으로 생성된다.")
        @Test
        void createsMoney_whenAmountIsZero() {
            // act
            Money money = Money.of(0L);

            // assert
            assertThat(money.getAmount()).isEqualTo(0L);
        }

        @DisplayName("금액이 음수이면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(longs = {-1L, -100L, Long.MIN_VALUE})
        void throwsBadRequest_whenAmountIsNegative(long negativeAmount) {
            // act
            CoreException ex = assertThrows(CoreException.class, () -> Money.of(negativeAmount));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("금액을 더할 때, ")
    @Nested
    class Plus {

        @DisplayName("두 금액의 합을 가진 새 Money 를 반환한다.")
        @Test
        void returnsSum() {
            // act
            Money result = Money.of(1000L).plus(Money.of(500L));

            // assert
            assertThat(result.getAmount()).isEqualTo(1500L);
        }

        @DisplayName("ZERO 에 더하면, 더한 금액과 동일한 Money 가 된다. (합산 시작값)")
        @Test
        void zeroIsIdentity() {
            // arrange
            Money money = Money.of(1000L);

            // act
            Money result = Money.ZERO.plus(money);

            // assert
            assertThat(result).isEqualTo(money);
        }

        @DisplayName("결과는 원본과 다른 인스턴스이며, 원본은 변하지 않는다.")
        @Test
        void isImmutable() {
            // arrange
            Money original = Money.of(1000L);

            // act
            Money result = original.plus(Money.of(500L));

            // assert
            assertAll(
                () -> assertThat(result).isNotSameAs(original),
                () -> assertThat(original.getAmount()).isEqualTo(1000L)
            );
        }

        @DisplayName("더할 금액이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenOtherIsNull() {
            // act
            CoreException ex = assertThrows(CoreException.class, () -> Money.of(1000L).plus(null));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("합이 long 범위를 넘으면, INTERNAL_ERROR 예외가 발생한다.")
        @Test
        void throwsInternalError_whenOverflow() {
            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> Money.of(Long.MAX_VALUE).plus(Money.of(1L)));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.INTERNAL_ERROR);
        }
    }

    @DisplayName("금액을 뺄 때, ")
    @Nested
    class Minus {

        @DisplayName("두 금액의 차를 가진 새 Money 를 반환한다.")
        @Test
        void returnsDifference() {
            // act
            Money result = Money.of(5000L).minus(Money.of(2000L));

            // assert
            assertThat(result.getAmount()).isEqualTo(3000L);
        }

        @DisplayName("같은 금액을 빼면, ZERO 와 동치인 Money 를 반환한다. (경계값)")
        @Test
        void returnsZero_whenSameAmount() {
            // act
            Money result = Money.of(1000L).minus(Money.of(1000L));

            // assert
            assertThat(result).isEqualTo(Money.ZERO);
        }

        @DisplayName("결과가 음수면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenResultNegative() {
            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> Money.of(1000L).minus(Money.of(2000L)));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("뺄 금액이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenOtherIsNull() {
            // act
            CoreException ex = assertThrows(CoreException.class, () -> Money.of(1000L).minus(null));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("금액에 수량을 곱할 때, ")
    @Nested
    class Times {

        @DisplayName("단가 × 수량의 라인 합계 Money 를 반환한다.")
        @Test
        void returnsLineTotal() {
            // act
            Money result = Money.of(1000L).times(Quantity.of(3));

            // assert
            assertThat(result.getAmount()).isEqualTo(3000L);
        }

        @DisplayName("수량이 0 이면, 0 원 Money 를 반환한다.")
        @Test
        void returnsZero_whenQuantityIsZero() {
            // act
            Money result = Money.of(1000L).times(Quantity.of(0));

            // assert
            assertThat(result).isEqualTo(Money.ZERO);
        }

        @DisplayName("수량이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenQuantityIsNull() {
            // act
            CoreException ex = assertThrows(CoreException.class, () -> Money.of(1000L).times(null));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("곱이 long 범위를 넘으면, INTERNAL_ERROR 예외가 발생한다.")
        @Test
        void throwsInternalError_whenOverflow() {
            // act
            CoreException ex = assertThrows(CoreException.class,
                () -> Money.of(Long.MAX_VALUE).times(Quantity.of(2)));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.INTERNAL_ERROR);
        }
    }

    @DisplayName("Money 의 동치를 판단할 때, ")
    @Nested
    class Equality {

        @DisplayName("금액이 같으면 동치이고 hashCode 도 같다.")
        @Test
        void equalWhenSameAmount() {
            // act + assert
            assertAll(
                () -> assertThat(Money.of(1000L)).isEqualTo(Money.of(1000L)),
                () -> assertThat(Money.of(1000L)).hasSameHashCodeAs(Money.of(1000L)),
                () -> assertThat(Money.of(0L)).isEqualTo(Money.ZERO)
            );
        }

        @DisplayName("금액이 다르거나 null/타입이 다르면 동치가 아니다.")
        @Test
        void notEqualWhenDifferent() {
            // act + assert
            assertAll(
                () -> assertThat(Money.of(1000L)).isNotEqualTo(Money.of(999L)),
                () -> assertThat(Money.of(1000L).equals(null)).isFalse(),
                () -> assertThat(Money.of(1000L).equals(Quantity.of(1000))).isFalse()
            );
        }
    }
}
