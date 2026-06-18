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

    private static final ZonedDateTime NOW = ZonedDateTime.parse("2026-06-06T00:00:00+09:00");

    private CouponPolicy availablePolicy() {
        ZonedDateTime expiredAt = ZonedDateTime.parse("2099-12-31T23:59:59+09:00");
        return new CouponPolicy("3천원 할인", CouponType.FIXED, 3_000L, 10_000L, expiredAt);
    }

    private CouponPolicy expiredPolicy() {
        ZonedDateTime expiredAt = ZonedDateTime.parse("2026-06-05T23:59:59+09:00");
        return new CouponPolicy("만료 쿠폰", CouponType.FIXED, 3_000L, 10_000L, expiredAt);
    }

    @DisplayName("쿠폰을 발급할 때, ")
    @Nested
    class Issue {

        @DisplayName("유효한 정책으로 발급하면, AVAILABLE 상태로 생성되고 아직 사용되지 않은 상태이다.")
        @Test
        void issuesAvailableCoupon_whenPolicyIsValid() {
            // given
            Long userId = 1L;
            CouponPolicy policy = availablePolicy();

            // when
            UserCoupon userCoupon = UserCoupon.issue(userId, policy, NOW);

            // then
            assertAll(
                () -> assertThat(userCoupon.getUserId()).isEqualTo(userId),
                () -> assertThat(userCoupon.getCouponPolicyId()).isEqualTo(policy.getId()),
                () -> assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.AVAILABLE),
                () -> assertThat(userCoupon.getUsedAt()).isNull()
            );
        }

        @DisplayName("userId 가 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenUserIdIsNull() {
            // given
            Long userId = null;
            CouponPolicy policy = availablePolicy();

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> UserCoupon.issue(userId, policy, NOW));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("사용자 ID 는 비어있을 수 없습니다.")
            );
        }

        @DisplayName("정책이 null 이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPolicyIsNull() {
            // given
            Long userId = 1L;
            CouponPolicy policy = null;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> UserCoupon.issue(userId, policy, NOW));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                () -> assertThat(result.getCustomMessage()).isEqualTo("쿠폰 정책은 비어있을 수 없습니다.")
            );
        }

        @DisplayName("이미 만료된 정책으로 발급하면, COUPON_EXPIRED 예외가 발생한다.")
        @Test
        void throwsCouponExpiredException_whenPolicyIsExpired() {
            // given
            Long userId = 1L;
            CouponPolicy policy = expiredPolicy();

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> UserCoupon.issue(userId, policy, NOW));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.COUPON_EXPIRED),
                () -> assertThat(result.getCustomMessage()).isEqualTo("만료된 쿠폰은 발급할 수 없습니다.")
            );
        }
    }

    @DisplayName("쿠폰을 사용할 때, ")
    @Nested
    class Use {

        private static final Long OWNER_ID = 1L;

        @DisplayName("모든 불변식을 충족하면, 할인액을 반환하고 USED 상태로 전이하며 사용 시각이 기록된다.")
        @Test
        void usesCouponAndReturnsDiscount_whenAllInvariantsAreSatisfied() {
            // given
            CouponPolicy policy = availablePolicy();
            UserCoupon userCoupon = UserCoupon.issue(OWNER_ID, policy, NOW);
            long orderAmount = 10_000L;

            // when
            long discount = userCoupon.use(OWNER_ID, orderAmount, NOW);

            // then
            assertAll(
                () -> assertThat(discount).isEqualTo(3_000L),
                () -> assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.USED),
                () -> assertThat(userCoupon.getUsedAt()).isEqualTo(NOW)
            );
        }

        @DisplayName("요청자가 소유자가 아니면, COUPON_NOT_OWNED 예외가 발생한다.")
        @Test
        void throwsCouponNotOwnedException_whenRequesterIsNotOwner() {
            // given
            CouponPolicy policy = availablePolicy();
            UserCoupon userCoupon = UserCoupon.issue(OWNER_ID, policy, NOW);
            Long otherUserId = 2L;
            long orderAmount = 10_000L;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> userCoupon.use(otherUserId, orderAmount, NOW));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.COUPON_NOT_OWNED),
                () -> assertThat(result.getCustomMessage()).isEqualTo("쿠폰을 찾을 수 없습니다.")
            );
        }

        @DisplayName("이미 사용된 쿠폰이면, COUPON_ALREADY_USED 예외가 발생한다.")
        @Test
        void throwsCouponAlreadyUsedException_whenCouponIsAlreadyUsed() {
            // given
            CouponPolicy policy = availablePolicy();
            UserCoupon userCoupon = UserCoupon.issue(OWNER_ID, policy, NOW);
            userCoupon.use(OWNER_ID, 10_000L, NOW);
            long orderAmount = 10_000L;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> userCoupon.use(OWNER_ID, orderAmount, NOW));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.COUPON_ALREADY_USED),
                () -> assertThat(result.getCustomMessage()).isEqualTo("이미 사용된 쿠폰입니다.")
            );
        }

        @DisplayName("사용 시점에 정책이 만료되었으면, COUPON_EXPIRED 예외가 발생한다.")
        @Test
        void throwsCouponExpiredException_whenPolicyIsExpiredAtUseTime() {
            // given
            CouponPolicy policy = availablePolicy();
            UserCoupon userCoupon = UserCoupon.issue(OWNER_ID, policy, NOW);
            ZonedDateTime afterExpiry = ZonedDateTime.parse("2100-01-01T00:00:00+09:00");
            long orderAmount = 10_000L;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> userCoupon.use(OWNER_ID, orderAmount, afterExpiry));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.COUPON_EXPIRED),
                () -> assertThat(result.getCustomMessage()).isEqualTo("만료된 쿠폰입니다.")
            );
        }

        @DisplayName("주문 금액이 최소 주문 금액에 미달하면, COUPON_MIN_ORDER_AMOUNT_NOT_MET 예외가 발생한다.")
        @Test
        void throwsMinOrderAmountNotMetException_whenOrderAmountIsBelowMinimum() {
            // given
            CouponPolicy policy = availablePolicy();
            UserCoupon userCoupon = UserCoupon.issue(OWNER_ID, policy, NOW);
            long orderAmount = 9_999L;

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> userCoupon.use(OWNER_ID, orderAmount, NOW));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.COUPON_MIN_ORDER_AMOUNT_NOT_MET),
                () -> assertThat(result.getCustomMessage()).isEqualTo("쿠폰 최소 주문 금액을 충족하지 못했습니다.")
            );
        }
    }

    @DisplayName("표시 상태를 파생할 때, ")
    @Nested
    class DisplayStatus {

        private static final Long OWNER_ID = 1L;

        @DisplayName("아직 사용되지 않았고 만료 전이면, AVAILABLE 을 반환한다.")
        @Test
        void returnsAvailable_whenNotUsedAndNotExpired() {
            // given
            UserCoupon userCoupon = UserCoupon.issue(OWNER_ID, availablePolicy(), NOW);

            // when
            CouponDisplayStatus status = userCoupon.displayStatus(NOW);

            // then
            assertThat(status).isEqualTo(CouponDisplayStatus.AVAILABLE);
        }

        @DisplayName("이미 사용되었으면, USED 를 반환한다.")
        @Test
        void returnsUsed_whenAlreadyUsed() {
            // given
            UserCoupon userCoupon = UserCoupon.issue(OWNER_ID, availablePolicy(), NOW);
            userCoupon.use(OWNER_ID, 10_000L, NOW);

            // when
            CouponDisplayStatus status = userCoupon.displayStatus(NOW);

            // then
            assertThat(status).isEqualTo(CouponDisplayStatus.USED);
        }

        @DisplayName("사용되지 않았지만 만료 시각이 지났으면, EXPIRED 를 반환한다.")
        @Test
        void returnsExpired_whenNotUsedButExpired() {
            // given
            UserCoupon userCoupon = UserCoupon.issue(OWNER_ID, availablePolicy(), NOW);
            ZonedDateTime afterExpiry = ZonedDateTime.parse("2100-01-01T00:00:00+09:00");

            // when
            CouponDisplayStatus status = userCoupon.displayStatus(afterExpiry);

            // then
            assertThat(status).isEqualTo(CouponDisplayStatus.EXPIRED);
        }

        @DisplayName("사용되었고 만료 시각도 지났으면, USED 가 EXPIRED 보다 우선한다.")
        @Test
        void returnsUsed_whenUsedAndExpired() {
            // given
            UserCoupon userCoupon = UserCoupon.issue(OWNER_ID, availablePolicy(), NOW);
            userCoupon.use(OWNER_ID, 10_000L, NOW);
            ZonedDateTime afterExpiry = ZonedDateTime.parse("2100-01-01T00:00:00+09:00");

            // when
            CouponDisplayStatus status = userCoupon.displayStatus(afterExpiry);

            // then
            assertThat(status).isEqualTo(CouponDisplayStatus.USED);
        }
    }
}
