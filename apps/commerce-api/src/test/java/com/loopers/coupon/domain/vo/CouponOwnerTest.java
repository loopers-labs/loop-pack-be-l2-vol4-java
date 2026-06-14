package com.loopers.coupon.domain.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponOwnerTest {

    @DisplayName("사용자 ID가 주어지면, 쿠폰 소유자를 생성한다.")
    @Test
    void createsCouponOwner_whenUserIdIsProvided() {
        // arrange
        Long userId = 1L;

        // act
        CouponOwner owner = CouponOwner.of(userId);

        // assert
        assertThat(owner.userId()).isEqualTo(userId);
    }

    @DisplayName("사용자 ID가 없으면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenUserIdIsNull() {
        // arrange
        Long userId = null;

        // act & assert
        assertThatThrownBy(() -> CouponOwner.of(userId))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("같은 사용자 ID가 주어지면, 같은 쿠폰 소유자로 판단한다.")
    @Test
    void returnsTrue_whenUserIdIsSame() {
        // arrange
        CouponOwner owner = CouponOwner.of(1L);

        // act
        boolean sameUser = owner.isSameUser(1L);

        // assert
        assertThat(sameUser).isTrue();
    }
}
