package com.loopers.domain.coupon;

import com.loopers.domain.product.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserCouponTest {

    private static final Long USER_ID = 10L;
    private static final Long ORDER_ID = 77L;
    private static final ZonedDateTime BASE =
            ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneId.of("Asia/Seoul"));

    private static CouponTemplate template(DiscountType type, long value, int validDays) {
        return CouponTemplate.create("쿠폰", DiscountPolicy.of(type, value), validDays);
    }

    @DisplayName("UserCoupon 을 issue 로 발급할 때, ")
    @Nested
    class Issue {

        @DisplayName("템플릿의 할인 정책·이름을 스냅샷하고 만료일을 확정하며 AVAILABLE 상태가 된다.")
        @Test
        void issuesSnapshotCoupon_whenValid() {
            CouponTemplate template = template(DiscountType.FIXED, 1_000L, 30);

            UserCoupon coupon = UserCoupon.issue(USER_ID, template, BASE);

            assertThat(coupon.getUserId()).isEqualTo(USER_ID);
            assertThat(coupon.getTemplateId()).isEqualTo(template.getId());
            assertThat(coupon.getCouponName()).isEqualTo("쿠폰");
            assertThat(coupon.getDiscountPolicy().getType()).isEqualTo(DiscountType.FIXED);
            assertThat(coupon.getDiscountPolicy().getValue()).isEqualTo(1_000L);
            assertThat(coupon.getStatus()).isEqualTo(CouponStatus.AVAILABLE);
            assertThat(coupon.getExpiresAt()).isEqualTo(BASE.plusDays(30));
            assertThat(coupon.getUsedAt()).isNull();
            assertThat(coupon.getOrderId()).isNull();
        }

        @DisplayName("userId 가 null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenUserIdIsNull() {
            CouponTemplate template = template(DiscountType.FIXED, 1_000L, 30);
            CoreException result = assertThrows(CoreException.class,
                    () -> UserCoupon.issue(null, template, BASE));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("템플릿이 null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenTemplateIsNull() {
            CoreException result = assertThrows(CoreException.class,
                    () -> UserCoupon.issue(USER_ID, null, BASE));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("calculateDiscount 는 스냅샷한 할인 정책으로 할인액을 계산한다.")
    @Test
    void calculateDiscount_delegatesToPolicy() {
        UserCoupon coupon = UserCoupon.issue(USER_ID, template(DiscountType.RATE, 10L, 30), BASE);
        Money discount = coupon.calculateDiscount(Money.of(10_000L));
        assertThat(discount.getAmount()).isEqualTo(1_000L);
    }

    @DisplayName("UserCoupon 을 use 로 사용할 때, ")
    @Nested
    class Use {

        @DisplayName("사용 가능 상태이면 USED 로 전이하고 사용 주문·시각을 기록한다.")
        @Test
        void marksUsed_whenAvailableAndNotExpired() {
            UserCoupon coupon = UserCoupon.issue(USER_ID, template(DiscountType.FIXED, 1_000L, 30), BASE);

            coupon.use(ORDER_ID, BASE);

            assertThat(coupon.getStatus()).isEqualTo(CouponStatus.USED);
            assertThat(coupon.getUsedAt()).isEqualTo(BASE);
            assertThat(coupon.getOrderId()).isEqualTo(ORDER_ID);
        }

        @DisplayName("만료일과 정확히 같은 시각에는 아직 사용할 수 있다(경계).")
        @Test
        void usable_atExactExpiry() {
            UserCoupon coupon = UserCoupon.issue(USER_ID, template(DiscountType.FIXED, 1_000L, 30), BASE);
            ZonedDateTime exactExpiry = BASE.plusDays(30);

            coupon.use(ORDER_ID, exactExpiry);

            assertThat(coupon.getStatus()).isEqualTo(CouponStatus.USED);
        }

        @DisplayName("이미 사용된 쿠폰이면 BAD_REQUEST 예외가 발생한다(재사용 방지).")
        @Test
        void throwsBadRequest_whenAlreadyUsed() {
            UserCoupon coupon = UserCoupon.issue(USER_ID, template(DiscountType.FIXED, 1_000L, 30), BASE);
            coupon.use(ORDER_ID, BASE);

            CoreException result = assertThrows(CoreException.class, () -> coupon.use(ORDER_ID, BASE));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("만료된 쿠폰이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenExpired() {
            UserCoupon coupon = UserCoupon.issue(USER_ID, template(DiscountType.FIXED, 1_000L, 30), BASE);
            ZonedDateTime afterExpiry = BASE.plusDays(30).plusSeconds(1);

            CoreException result = assertThrows(CoreException.class, () -> coupon.use(ORDER_ID, afterExpiry));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("사용 주문이 null 이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenOrderIdIsNull() {
            UserCoupon coupon = UserCoupon.issue(USER_ID, template(DiscountType.FIXED, 1_000L, 30), BASE);
            CoreException result = assertThrows(CoreException.class, () -> coupon.use(null, BASE));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("displayStatus 는 ")
    @Nested
    class DisplayStatus {

        @DisplayName("사용 가능하고 만료 전이면 AVAILABLE 을 반환한다.")
        @Test
        void returnsAvailable_whenUsableAndNotExpired() {
            UserCoupon coupon = UserCoupon.issue(USER_ID, template(DiscountType.FIXED, 1_000L, 30), BASE);
            assertThat(coupon.displayStatus(BASE.plusDays(1))).isEqualTo(CouponStatus.AVAILABLE);
        }

        @DisplayName("사용 가능하지만 만료일이 지났으면 EXPIRED 를 파생한다.")
        @Test
        void returnsExpired_whenAvailableButPastExpiry() {
            UserCoupon coupon = UserCoupon.issue(USER_ID, template(DiscountType.FIXED, 1_000L, 30), BASE);
            assertThat(coupon.displayStatus(BASE.plusDays(31))).isEqualTo(CouponStatus.EXPIRED);
        }

        @DisplayName("사용 완료된 쿠폰은 만료일이 지났어도 USED 를 반환한다.")
        @Test
        void returnsUsed_evenWhenPastExpiry() {
            UserCoupon coupon = UserCoupon.issue(USER_ID, template(DiscountType.FIXED, 1_000L, 30), BASE);
            coupon.use(ORDER_ID, BASE);
            assertThat(coupon.displayStatus(BASE.plusDays(31))).isEqualTo(CouponStatus.USED);
        }
    }

    @DisplayName("isUsable 은 ")
    @Nested
    class IsUsable {

        @DisplayName("사용 가능하고 만료 전이면 true 를 반환한다.")
        @Test
        void returnsTrue_whenAvailableAndNotExpired() {
            UserCoupon coupon = UserCoupon.issue(USER_ID, template(DiscountType.FIXED, 1_000L, 30), BASE);
            assertThat(coupon.isUsable(BASE.plusDays(1))).isTrue();
        }

        @DisplayName("만료됐으면 false 를 반환한다.")
        @Test
        void returnsFalse_whenExpired() {
            UserCoupon coupon = UserCoupon.issue(USER_ID, template(DiscountType.FIXED, 1_000L, 30), BASE);
            assertThat(coupon.isUsable(BASE.plusDays(31))).isFalse();
        }

        @DisplayName("이미 사용됐으면 false 를 반환한다.")
        @Test
        void returnsFalse_whenUsed() {
            UserCoupon coupon = UserCoupon.issue(USER_ID, template(DiscountType.FIXED, 1_000L, 30), BASE);
            coupon.use(ORDER_ID, BASE);
            assertThat(coupon.isUsable(BASE.plusDays(1))).isFalse();
        }
    }

    @DisplayName("isOwnedBy 는 ")
    @Nested
    class IsOwnedBy {

        @DisplayName("동일한 userId 면 true 를 반환한다.")
        @Test
        void returnsTrue_whenSameUserId() {
            UserCoupon coupon = UserCoupon.issue(USER_ID, template(DiscountType.FIXED, 1_000L, 30), BASE);
            assertThat(coupon.isOwnedBy(USER_ID)).isTrue();
        }

        @DisplayName("다른 userId 면 false 를 반환한다.")
        @Test
        void returnsFalse_whenDifferentUserId() {
            UserCoupon coupon = UserCoupon.issue(USER_ID, template(DiscountType.FIXED, 1_000L, 30), BASE);
            assertThat(coupon.isOwnedBy(999L)).isFalse();
        }

        @DisplayName("null 이면 false 를 반환한다.")
        @Test
        void returnsFalse_whenNull() {
            UserCoupon coupon = UserCoupon.issue(USER_ID, template(DiscountType.FIXED, 1_000L, 30), BASE);
            assertThat(coupon.isOwnedBy(null)).isFalse();
        }
    }

    @DisplayName("assertUsableBy 는 ")
    @Nested
    class AssertUsableBy {

        @DisplayName("본인 소유이고 사용 가능하면 통과한다.")
        @Test
        void passes_whenOwnedAndUsable() {
            UserCoupon coupon = UserCoupon.issue(USER_ID, template(DiscountType.FIXED, 1_000L, 30), BASE);
            coupon.assertUsableBy(USER_ID, BASE); // 예외 없음
        }

        @DisplayName("본인 소유가 아니면 FORBIDDEN 예외가 발생한다.")
        @Test
        void throwsForbidden_whenNotOwned() {
            UserCoupon coupon = UserCoupon.issue(USER_ID, template(DiscountType.FIXED, 1_000L, 30), BASE);
            CoreException result = assertThrows(CoreException.class,
                    () -> coupon.assertUsableBy(999L, BASE));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
        }

        @DisplayName("이미 사용된 쿠폰이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenAlreadyUsed() {
            UserCoupon coupon = UserCoupon.issue(USER_ID, template(DiscountType.FIXED, 1_000L, 30), BASE);
            coupon.use(ORDER_ID, BASE);
            CoreException result = assertThrows(CoreException.class,
                    () -> coupon.assertUsableBy(USER_ID, BASE));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("만료된 쿠폰이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenExpired() {
            UserCoupon coupon = UserCoupon.issue(USER_ID, template(DiscountType.FIXED, 1_000L, 30), BASE);
            CoreException result = assertThrows(CoreException.class,
                    () -> coupon.assertUsableBy(USER_ID, BASE.plusDays(31)));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
