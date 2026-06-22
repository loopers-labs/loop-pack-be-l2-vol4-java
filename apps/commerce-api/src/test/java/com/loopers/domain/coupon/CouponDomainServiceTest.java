package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class CouponDomainServiceTest {

    private CouponDomainService couponDomainService;

    @BeforeEach
    void setUp() {
        couponDomainService = new CouponDomainService();
    }

    @DisplayName("할인 금액을 계산할 때,")
    @Nested
    class CalculateDiscount {

        @DisplayName("FIXED 타입: 주문 금액이 할인 금액보다 크면 value만큼 할인된다.")
        @Test
        void calculateDiscount_fixed_returnsValue() {
            // arrange
            CouponTemplateModel template = new CouponTemplateModel(
                "1000원 쿠폰", CouponType.FIXED, 1000L, null, LocalDateTime.now().plusDays(7)
            );

            // act
            long discount = couponDomainService.calculateDiscount(template, 10000L);

            // assert
            assertThat(discount).isEqualTo(1000L);
        }

        @DisplayName("FIXED 타입: 할인 금액이 주문 금액보다 크면 주문 금액만큼만 할인된다.")
        @Test
        void calculateDiscount_fixed_cappedAtOrderAmount() {
            // arrange
            CouponTemplateModel template = new CouponTemplateModel(
                "5000원 쿠폰", CouponType.FIXED, 5000L, null, LocalDateTime.now().plusDays(7)
            );

            // act
            long discount = couponDomainService.calculateDiscount(template, 1000L);

            // assert
            assertThat(discount).isEqualTo(1000L);
        }

        @DisplayName("RATE 타입: 주문 금액 × 할인율 / 100이 할인된다.")
        @Test
        void calculateDiscount_rate_returnsPercentage() {
            // arrange
            CouponTemplateModel template = new CouponTemplateModel(
                "10% 쿠폰", CouponType.RATE, 10L, null, LocalDateTime.now().plusDays(7)
            );

            // act
            long discount = couponDomainService.calculateDiscount(template, 10000L);

            // assert
            assertThat(discount).isEqualTo(1000L);
        }

        @DisplayName("template이 null이면 할인 금액으로 0을 반환한다.")
        @Test
        void calculateDiscount_nullTemplate_returnsZero() {
            // act
            long discount = couponDomainService.calculateDiscount(null, 10000L);

            // assert
            assertThat(discount).isEqualTo(0L);
        }
    }

    @DisplayName("최소 주문 금액을 검증할 때,")
    @Nested
    class ValidateMinOrderAmount {

        @DisplayName("template이 null이면 검증을 통과한다.")
        @Test
        void validate_nullTemplate_passes() {
            assertThatCode(() -> couponDomainService.validateMinOrderAmount(null, 10000L))
                .doesNotThrowAnyException();
        }

        @DisplayName("minOrderAmount가 null이면 검증을 통과한다.")
        @Test
        void validate_nullMinOrderAmount_passes() {
            // arrange
            CouponTemplateModel template = new CouponTemplateModel(
                "쿠폰", CouponType.FIXED, 1000L, null, LocalDateTime.now().plusDays(7)
            );

            // assert
            assertThatCode(() -> couponDomainService.validateMinOrderAmount(template, 5000L))
                .doesNotThrowAnyException();
        }

        @DisplayName("orderAmount >= minOrderAmount이면 검증을 통과한다.")
        @Test
        void validate_orderAmountMeetsMinimum_passes() {
            // arrange
            CouponTemplateModel template = new CouponTemplateModel(
                "쿠폰", CouponType.FIXED, 1000L, 5000L, LocalDateTime.now().plusDays(7)
            );

            // assert
            assertThatCode(() -> couponDomainService.validateMinOrderAmount(template, 5000L))
                .doesNotThrowAnyException();
        }

        @DisplayName("orderAmount < minOrderAmount이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void validate_orderAmountBelowMinimum_throwsBadRequest() {
            // arrange
            CouponTemplateModel template = new CouponTemplateModel(
                "쿠폰", CouponType.FIXED, 1000L, 5000L, LocalDateTime.now().plusDays(7)
            );

            // act & assert
            assertThatThrownBy(() -> couponDomainService.validateMinOrderAmount(template, 4999L))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
