package com.loopers.domain.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class QuantityTest {

    @DisplayName("정상 수량으로 Quantity 를 생성할 수 있다.")
    @Test
    void createsQuantity_withNonNegativeValue() {
        Quantity quantity = Quantity.of(10);
        assertThat(quantity.value()).isEqualTo(10);
    }

    @DisplayName("0 수량으로도 Quantity 를 생성할 수 있다.")
    @Test
    void createsQuantity_withZero() {
        Quantity quantity = Quantity.of(0);
        assertThat(quantity.value()).isEqualTo(0);
        assertThat(quantity.isZero()).isTrue();
    }

    @DisplayName("음수 수량은 BAD_REQUEST 예외가 발생한다.")
    @Test
    void throwsBadRequest_whenValueIsNegative() {
        CoreException result = assertThrows(CoreException.class, () -> Quantity.of(-1));
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("plus 는 두 Quantity 의 합으로 새 객체를 만든다.")
    @Test
    void plus_returnsSumAsNewInstance() {
        Quantity a = Quantity.of(3);
        Quantity b = Quantity.of(2);

        Quantity result = a.plus(b);

        assertThat(result.value()).isEqualTo(5);
        assertThat(a.value()).isEqualTo(3); // 원본 불변
    }

    @DisplayName("minus 결과가 음수가 되면 BAD_REQUEST 예외가 발생한다.")
    @Test
    void minus_throwsBadRequest_whenResultIsNegative() {
        Quantity a = Quantity.of(1);
        Quantity b = Quantity.of(2);

        CoreException result = assertThrows(CoreException.class, () -> a.minus(b));

        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("isGreaterThanOrEqual 은 같거나 큰 경우 true 를 반환한다.")
    @Test
    void isGreaterThanOrEqual_returnsTrue_whenEqualOrGreater() {
        Quantity a = Quantity.of(5);
        Quantity b = Quantity.of(5);
        Quantity c = Quantity.of(3);

        assertThat(a.isGreaterThanOrEqual(b)).isTrue();
        assertThat(a.isGreaterThanOrEqual(c)).isTrue();
        assertThat(c.isGreaterThanOrEqual(a)).isFalse();
    }

    @DisplayName("같은 값의 Quantity 는 equals 가 true 다.")
    @Test
    void equals_returnsTrue_whenSameValue() {
        Quantity a = Quantity.of(5);
        Quantity b = Quantity.of(5);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
