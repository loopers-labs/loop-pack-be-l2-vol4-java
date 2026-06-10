package com.loopers.domain.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class UserCouponModelTest {

    private static final ZonedDateTime NOW = ZonedDateTime.now();

    private static CouponModel coupon(DiscountType type, int value, Integer minOrderAmount, ZonedDateTime expiredAt, ZonedDateTime issuedAt) {
        CouponModel coupon = CouponModel.builder()
            .rawName("신규 가입 쿠폰")
            .type(type)
            .rawValue(value)
            .rawMinOrderAmount(minOrderAmount)
            .rawExpiredAt(expiredAt)
            .now(issuedAt)
            .build();
        ReflectionTestUtils.setField(coupon, "id", 1L);

        return coupon;
    }

    private static UserCouponModel availableCoupon(DiscountType type, int value, Integer minOrderAmount) {
        return UserCouponModel.issue(100L, coupon(type, value, minOrderAmount, NOW.plusDays(7), NOW));
    }

    private static UserCouponModel expiredCoupon() {
        ZonedDateTime pastExpiredAt = NOW.minusDays(1);

        return UserCouponModel.issue(100L, coupon(DiscountType.FIXED, 5_000, 10_000, pastExpiredAt, pastExpiredAt.minusDays(1)));
    }

    private static UserCouponModel usedCoupon(ZonedDateTime expiredAt, ZonedDateTime usedAt) {
        return UserCouponModel.builder()
            .userId(100L)
            .couponId(1L)
            .name("신규 가입 쿠폰")
            .discountType(DiscountType.FIXED)
            .discountValue(5_000)
            .minOrderAmount(10_000)
            .expiredAt(expiredAt)
            .usedAt(usedAt)
            .build();
    }

    @DisplayName("쿠폰을 발급할 때,")
    @Nested
    class Issue {

        private final ZonedDateTime now = ZonedDateTime.now();
        private final ZonedDateTime expiredAt = now.plusDays(7);

        private CouponModel coupon() {
            return coupon(DiscountType.FIXED, 5_000);
        }

        private CouponModel coupon(DiscountType type, int value) {
            CouponModel coupon = CouponModel.builder()
                .rawName("신규 가입 쿠폰")
                .type(type)
                .rawValue(value)
                .rawMinOrderAmount(10_000)
                .rawExpiredAt(expiredAt)
                .now(now)
                .build();
            ReflectionTestUtils.setField(coupon, "id", 1L);

            return coupon;
        }

        @DisplayName("템플릿 정보를 스냅샷으로 복사한 발급 쿠폰이 생성된다.")
        @Test
        void copiesTemplateSnapshot() {
            // arrange
            CouponModel coupon = coupon();

            // act
            UserCouponModel userCoupon = UserCouponModel.issue(100L, coupon);

            // assert
            assertAll(
                () -> assertThat(userCoupon.getUserId()).isEqualTo(100L),
                () -> assertThat(userCoupon.getCouponId()).isEqualTo(1L),
                () -> assertThat(userCoupon.getName()).isEqualTo("신규 가입 쿠폰"),
                () -> assertThat(userCoupon.getDiscountType()).isEqualTo(DiscountType.FIXED),
                () -> assertThat(userCoupon.getDiscountValue()).isEqualTo(5_000),
                () -> assertThat(userCoupon.getMinOrderAmount()).isEqualTo(10_000),
                () -> assertThat(userCoupon.getExpiredAt()).isEqualTo(expiredAt)
            );
        }

        @DisplayName("정률 템플릿에서 발급하면 할인 타입·할인 값이 그대로 스냅샷된다.")
        @Test
        void copiesRateSnapshot() {
            // arrange
            CouponModel coupon = coupon(DiscountType.RATE, 20);

            // act
            UserCouponModel userCoupon = UserCouponModel.issue(100L, coupon);

            // assert
            assertAll(
                () -> assertThat(userCoupon.getDiscountType()).isEqualTo(DiscountType.RATE),
                () -> assertThat(userCoupon.getDiscountValue()).isEqualTo(20)
            );
        }

        @DisplayName("발급 직후에는 사용 시각이 없다.")
        @Test
        void hasNoUsedAt_whenJustIssued() {
            // arrange
            CouponModel coupon = coupon();

            // act
            UserCouponModel userCoupon = UserCouponModel.issue(100L, coupon);

            // assert
            assertThat(userCoupon.getUsedAt()).isNull();
        }
    }

    @DisplayName("발급 쿠폰의 상태를 판정할 때,")
    @Nested
    class GetStatus {

        @DisplayName("미사용이고 만료 시각이 지나지 않았으면 AVAILABLE이다.")
        @Test
        void returnsAvailable_whenUnusedAndNotExpired() {
            // arrange
            UserCouponModel userCoupon = availableCoupon(DiscountType.FIXED, 5_000, 10_000);

            // act & assert
            assertThat(userCoupon.getStatus(NOW)).isEqualTo(UserCouponStatus.AVAILABLE);
        }

        @DisplayName("미사용이고 만료 시각이 지났으면 EXPIRED이다.")
        @Test
        void returnsExpired_whenUnusedAndExpired() {
            // arrange
            UserCouponModel userCoupon = expiredCoupon();

            // act & assert
            assertThat(userCoupon.getStatus(NOW)).isEqualTo(UserCouponStatus.EXPIRED);
        }

        @DisplayName("사용했으면 만료 시각이 지나지 않아도 USED이다.")
        @Test
        void returnsUsed_whenUsedAndNotExpired() {
            // arrange
            UserCouponModel userCoupon = usedCoupon(NOW.plusDays(7), NOW);

            // act & assert
            assertThat(userCoupon.getStatus(NOW)).isEqualTo(UserCouponStatus.USED);
        }

        @DisplayName("사용한 뒤 만료 시각이 지나도 USED가 만료보다 우선한다.")
        @Test
        void returnsUsed_whenUsedAndExpired() {
            // arrange
            UserCouponModel userCoupon = usedCoupon(NOW.minusDays(1), NOW.minusDays(3));

            // act & assert
            assertThat(userCoupon.getStatus(NOW)).isEqualTo(UserCouponStatus.USED);
        }
    }

    @DisplayName("발급 쿠폰을 주문에 적용할 때,")
    @Nested
    class Apply {

        @DisplayName("사용 가능하고 최소 주문 금액을 충족하면 할인액을 반환하고 사용 시각을 기록한다.")
        @Test
        void returnsDiscount_andRecordsUsedAt() {
            // arrange
            UserCouponModel userCoupon = availableCoupon(DiscountType.FIXED, 5_000, 10_000);

            // act
            int discountAmount = userCoupon.apply(10_000, NOW);

            // assert
            assertAll(
                () -> assertThat(discountAmount).isEqualTo(5_000),
                () -> assertThat(userCoupon.getUsedAt()).isEqualTo(NOW)
            );
        }

        @DisplayName("이미 사용했거나 만료된 쿠폰이면 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenNotAvailable() {
            // arrange
            UserCouponModel usedCoupon = usedCoupon(NOW.plusDays(7), NOW);
            UserCouponModel expiredCoupon = expiredCoupon();

            // act & assert
            assertAll(
                () -> assertThatThrownBy(() -> usedCoupon.apply(10_000, NOW))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.CONFLICT),
                () -> assertThatThrownBy(() -> expiredCoupon.apply(10_000, NOW))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.CONFLICT)
            );
        }

        @DisplayName("할인 전 주문 금액이 최소 주문 금액에 미치지 못하면 CONFLICT 예외가 발생하고 사용 시각은 기록되지 않는다.")
        @Test
        void throwsConflict_andStaysUnused_whenOrderAmountIsBelowMinimum() {
            // arrange
            UserCouponModel userCoupon = availableCoupon(DiscountType.FIXED, 5_000, 10_000);

            // act & assert (최소금액 검증이 사용 처리보다 먼저라 실패 시 흔적이 남지 않는다)
            assertAll(
                () -> assertThatThrownBy(() -> userCoupon.apply(9_999, NOW))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.CONFLICT),
                () -> assertThat(userCoupon.getUsedAt()).isNull()
            );
        }

        @DisplayName("할인 전 주문 금액이 최소 주문 금액과 같으면 적용된다.")
        @Test
        void applies_whenOrderAmountEqualsMinimum() {
            // arrange
            UserCouponModel userCoupon = availableCoupon(DiscountType.FIXED, 5_000, 10_000);

            // act & assert
            assertThat(userCoupon.apply(10_000, NOW)).isEqualTo(5_000);
        }

        @DisplayName("정액 쿠폰의 할인 값이 주문 금액보다 크면 주문 금액으로 캡된다.")
        @Test
        void capsAtOrderAmount_whenFixedValueExceedsOrderAmount() {
            // arrange
            UserCouponModel userCoupon = availableCoupon(DiscountType.FIXED, 50_000, null);

            // act & assert
            assertThat(userCoupon.apply(30_000, NOW)).isEqualTo(30_000);
        }

        @DisplayName("정률 쿠폰은 주문 금액의 비율을 소수점 이하 내림한 정수로 적용한다.")
        @Test
        void floorsRateDiscount() {
            // arrange (10,001원의 10% = 1,000.1원 → 1,000원)
            UserCouponModel userCoupon = availableCoupon(DiscountType.RATE, 10, null);

            // act & assert
            assertThat(userCoupon.apply(10_001, NOW)).isEqualTo(1_000);
        }
    }
}
