package com.loopers.domain.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MoneyTest {

    @DisplayName("정상 금액으로 Money 를 생성할 수 있다.")
    @Test
    void createsMoney_withNonNegativeAmount() {
        Money money = Money.of(1000L);
        assertThat(money.amount()).isEqualTo(1000L);
    }

    @DisplayName("0원으로도 Money 를 생성할 수 있다.")
    @Test
    void createsMoney_withZero() {
        Money money = Money.of(0L);
        assertThat(money.amount()).isEqualTo(0L);
    }

    @DisplayName("음수 금액은 BAD_REQUEST 예외가 발생한다.")
    @Test
    void throwsBadRequest_whenAmountIsNegative() {
        CoreException result = assertThrows(CoreException.class, () -> Money.of(-1L));
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("plus 는 두 Money 의 합으로 새 객체를 만든다.")
    @Test
    void plus_returnsSumAsNewInstance() {
        Money a = Money.of(1000L);
        Money b = Money.of(500L);

        Money result = a.plus(b);

        assertThat(result.amount()).isEqualTo(1500L);
        assertThat(a.amount()).isEqualTo(1000L); // 원본 불변
    }

    @DisplayName("minus 결과가 음수가 되면 BAD_REQUEST 예외가 발생한다.")
    @Test
    void minus_throwsBadRequest_whenResultIsNegative() {
        Money a = Money.of(100L);
        Money b = Money.of(200L);

        CoreException result = assertThrows(CoreException.class, () -> a.minus(b));

        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("times 는 수량을 곱한 새 Money 를 만든다.")
    @Test
    void times_returnsMultipliedAsNewInstance() {
        Money price = Money.of(1000L);

        Money total = price.times(3);

        assertThat(total.amount()).isEqualTo(3000L);
    }

    @DisplayName("isGreaterThanOrEqual 은 같거나 큰 경우 true 를 반환한다.")
    @Test
    void isGreaterThanOrEqual_returnsTrue_whenEqualOrGreater() {
        Money a = Money.of(1000L);
        Money b = Money.of(1000L);
        Money c = Money.of(500L);

        assertThat(a.isGreaterThanOrEqual(b)).isTrue();
        assertThat(a.isGreaterThanOrEqual(c)).isTrue();
        assertThat(c.isGreaterThanOrEqual(a)).isFalse();
    }

    @DisplayName("같은 금액의 Money 는 equals 가 true 다.")
    @Test
    void equals_returnsTrue_whenSameAmount() {
        Money a = Money.of(1000L);
        Money b = Money.of(1000L);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
