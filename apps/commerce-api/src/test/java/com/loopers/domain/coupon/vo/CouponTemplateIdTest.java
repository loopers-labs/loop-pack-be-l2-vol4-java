package com.loopers.domain.coupon.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponTemplateIdTest {

    @DisplayName("쿠폰 템플릿 ID가 주어지면, 쿠폰 템플릿 ID를 생성한다.")
    @Test
    void createsCouponTemplateId_whenValueIsProvided() {
        // arrange
        Long value = 10L;

        // act
        CouponTemplateId couponTemplateId = CouponTemplateId.of(value);

        // assert
        assertThat(couponTemplateId.value()).isEqualTo(value);
    }

    @DisplayName("쿠폰 템플릿 ID가 없으면, BAD_REQUEST 예외를 던진다.")
    @Test
    void throwsBadRequest_whenValueIsNull() {
        // arrange
        Long value = null;

        // act & assert
        assertThatThrownBy(() -> CouponTemplateId.of(value))
            .isInstanceOf(CoreException.class)
            .extracting("errorType")
            .isEqualTo(ErrorType.BAD_REQUEST);
    }
}
