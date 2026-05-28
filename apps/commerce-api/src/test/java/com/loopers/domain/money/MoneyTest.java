package com.loopers.domain.money;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyTest {
    @DisplayName("금액을 생성할 때, ")
    @Nested
    class Create {
        @DisplayName("0 이상의 금액이면, 정상적으로 생성된다.")
        @Test
        void createsMoney_whenAmountIsZeroOrPositive() {
            // arrange
            BigDecimal amount = BigDecimal.valueOf(1000);

            // act
            Money money = new Money(amount);

            // assert
            assertThat(money.getAmount()).isEqualByComparingTo(amount);
        }

        @DisplayName("음수 금액이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenAmountIsNegative() {
            // arrange
            BigDecimal amount = BigDecimal.valueOf(-1);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new Money(amount);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("금액이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenAmountIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new Money(null);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("금액에 배수를 곱할 때, ")
    @Nested
    class Multiply {
        @DisplayName("주어진 배수만큼 곱한 새 Money를 반환한다.")
        @Test
        void returnsMultipliedMoney() {
            // arrange
            Money money = new Money(BigDecimal.valueOf(1000));

            // act
            Money result = money.multiply(3);

            // assert
            assertThat(result.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(3000));
        }
    }

    @DisplayName("금액을 더할 때, ")
    @Nested
    class Plus {
        @DisplayName("두 Money를 더한 새 Money를 반환한다.")
        @Test
        void returnsSumOfMoney() {
            // arrange
            Money a = new Money(BigDecimal.valueOf(1000));
            Money b = new Money(BigDecimal.valueOf(500));

            // act
            Money result = a.plus(b);

            // assert
            assertThat(result.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(1500));
        }
    }
}
