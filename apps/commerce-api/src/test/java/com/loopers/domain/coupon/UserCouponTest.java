package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserCouponTest {

    private static final ZonedDateTime FUTURE = ZonedDateTime.now().plusDays(30);
    private static final ZonedDateTime PAST = ZonedDateTime.now().minusDays(1);

    private static CouponTemplate fixedTemplate() {
        return new CouponTemplate("정액 쿠폰", CouponType.FIXED, 1000L, null, FUTURE);
    }

    @DisplayName("사용자 쿠폰을 발급할 때,")
    @Nested
    class Issue {

        @DisplayName("발급 시 AVAILABLE 상태로 생성되며, 템플릿의 만료일이 복사된다.")
        @Test
        void issues_with_available_status_and_copied_expiry() {
            CouponTemplate template = fixedTemplate();

            UserCoupon userCoupon = new UserCoupon(1L, template);

            assertAll(
                () -> assertThat(userCoupon.getMemberId()).isEqualTo(1L),
                () -> assertThat(userCoupon.getStatus()).isEqualTo(CouponStatus.AVAILABLE),
                () -> assertThat(userCoupon.getExpiredAt()).isEqualTo(template.getExpiredAt()),
                () -> assertThat(userCoupon.getUsedAt()).isNull()
            );
        }

        @DisplayName("memberId가 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_member_id_is_null() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new UserCoupon(null, fixedTemplate()));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("template이 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_template_is_null() {
            CoreException ex = assertThrows(CoreException.class,
                () -> new UserCoupon(1L, null));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("쿠폰 사용 가능 여부를 확인할 때,")
    @Nested
    class IsAvailable {

        @DisplayName("AVAILABLE 상태이고 만료되지 않았으면 true를 반환한다.")
        @Test
        void returns_true_when_available_and_not_expired() {
            UserCoupon userCoupon = new UserCoupon(1L, fixedTemplate());

            assertThat(userCoupon.isAvailable()).isTrue();
        }

        @DisplayName("USED 상태이면 false를 반환한다.")
        @Test
        void returns_false_when_used() {
            UserCoupon userCoupon = new UserCoupon(1L, fixedTemplate());
            userCoupon.use();

            assertThat(userCoupon.isAvailable()).isFalse();
        }

        @DisplayName("만료일이 지났으면 false를 반환한다.")
        @Test
        void returns_false_when_expired() {
            CouponTemplate expiredTemplate = new CouponTemplate("만료 쿠폰", CouponType.FIXED, 1000L, null, PAST);
            UserCoupon userCoupon = new UserCoupon(1L, expiredTemplate);

            assertThat(userCoupon.isAvailable()).isFalse();
        }
    }

    @DisplayName("쿠폰을 사용할 때,")
    @Nested
    class Use {

        @DisplayName("AVAILABLE 상태의 쿠폰은 USED 상태로 전환되고, usedAt이 기록된다.")
        @Test
        void uses_available_coupon() {
            UserCoupon userCoupon = new UserCoupon(1L, fixedTemplate());

            userCoupon.use();

            assertAll(
                () -> assertThat(userCoupon.getStatus()).isEqualTo(CouponStatus.USED),
                () -> assertThat(userCoupon.getUsedAt()).isNotNull()
            );
        }

        @DisplayName("이미 사용된 쿠폰을 다시 사용하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_already_used() {
            UserCoupon userCoupon = new UserCoupon(1L, fixedTemplate());
            userCoupon.use();

            CoreException ex = assertThrows(CoreException.class, userCoupon::use);

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("만료된 쿠폰을 사용하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_expired() {
            CouponTemplate expiredTemplate = new CouponTemplate("만료 쿠폰", CouponType.FIXED, 1000L, null, PAST);
            UserCoupon userCoupon = new UserCoupon(1L, expiredTemplate);

            CoreException ex = assertThrows(CoreException.class, userCoupon::use);

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("쿠폰 소유자를 확인할 때,")
    @Nested
    class IsOwnedBy {

        @DisplayName("본인의 쿠폰이면 true를 반환한다.")
        @Test
        void returns_true_when_owner_matches() {
            UserCoupon userCoupon = new UserCoupon(1L, fixedTemplate());

            assertThat(userCoupon.isOwnedBy(1L)).isTrue();
        }

        @DisplayName("타인의 쿠폰이면 false를 반환한다.")
        @Test
        void returns_false_when_owner_does_not_match() {
            UserCoupon userCoupon = new UserCoupon(1L, fixedTemplate());

            assertThat(userCoupon.isOwnedBy(99L)).isFalse();
        }
    }
}
