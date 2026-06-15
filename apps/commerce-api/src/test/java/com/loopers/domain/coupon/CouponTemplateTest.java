package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponTemplateTest {

    private static DiscountPolicy fixed(long value) {
        return DiscountPolicy.of(DiscountType.FIXED, value);
    }

    @DisplayName("CouponTemplate 를 create 로 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("정상 값이면 이름·할인 정책·유효일수가 설정된다.")
        @Test
        void createsTemplate_whenValid() {
            CouponTemplate template = CouponTemplate.create("신규가입 1만원 할인", fixed(10_000L), 30);

            assertThat(template.getName()).isEqualTo("신규가입 1만원 할인");
            assertThat(template.getDiscountPolicy().getType()).isEqualTo(DiscountType.FIXED);
            assertThat(template.getDiscountPolicy().getValue()).isEqualTo(10_000L);
            assertThat(template.getValidDays()).isEqualTo(30);
            assertThat(template.isDeleted()).isFalse();
        }

        @DisplayName("이름이 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        void throwsBadRequest_whenNameIsBlank(String name) {
            CoreException result = assertThrows(CoreException.class,
                    () -> CouponTemplate.create(name, fixed(1_000L), 30));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("할인 정책이 null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenDiscountPolicyIsNull() {
            CoreException result = assertThrows(CoreException.class,
                    () -> CouponTemplate.create("쿠폰", null, 30));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("유효일수가 1 미만이면 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(ints = {0, -1})
        void throwsBadRequest_whenValidDaysIsNotPositive(int validDays) {
            CoreException result = assertThrows(CoreException.class,
                    () -> CouponTemplate.create("쿠폰", fixed(1_000L), validDays));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("CouponTemplate 를 modify 할 때, ")
    @Nested
    class Modify {

        @DisplayName("정상 값이면 이름·할인 정책·유효일수가 변경된다.")
        @Test
        void modifiesTemplate_whenValid() {
            CouponTemplate template = CouponTemplate.create("쿠폰", fixed(1_000L), 30);

            template.modify("여름세일 10%", DiscountPolicy.of(DiscountType.RATE, 10L), 7);

            assertThat(template.getName()).isEqualTo("여름세일 10%");
            assertThat(template.getDiscountPolicy().getType()).isEqualTo(DiscountType.RATE);
            assertThat(template.getDiscountPolicy().getValue()).isEqualTo(10L);
            assertThat(template.getValidDays()).isEqualTo(7);
        }

        @DisplayName("유효일수가 1 미만이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenValidDaysIsNotPositive() {
            CouponTemplate template = CouponTemplate.create("쿠폰", fixed(1_000L), 30);
            CoreException result = assertThrows(CoreException.class,
                    () -> template.modify("쿠폰", fixed(1_000L), 0));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("issueExpiresAt 은 ")
    @Nested
    class IssueExpiresAt {

        @DisplayName("발급 시각에 유효일수를 더한 만료 시각을 돌려준다.")
        @Test
        void returnsIssuedAtPlusValidDays() {
            CouponTemplate template = CouponTemplate.create("쿠폰", fixed(1_000L), 30);
            ZonedDateTime issuedAt = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneId.of("Asia/Seoul"));

            assertThat(template.issueExpiresAt(issuedAt)).isEqualTo(issuedAt.plusDays(30));
        }

        @DisplayName("발급 시각이 null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenIssuedAtIsNull() {
            CouponTemplate template = CouponTemplate.create("쿠폰", fixed(1_000L), 30);
            CoreException result = assertThrows(CoreException.class, () -> template.issueExpiresAt(null));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("delete 후 isDeleted 는 true 를 반환한다.")
    @Test
    void isDeletedTrue_afterDelete() {
        CouponTemplate template = CouponTemplate.create("쿠폰", fixed(1_000L), 30);
        template.delete();
        assertThat(template.isDeleted()).isTrue();
    }
}
