package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserCouponModelTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TEMPLATE_ID = UUID.randomUUID();
    private static final ZonedDateTime FUTURE = ZonedDateTime.now().plusDays(1);
    private static final ZonedDateTime PAST = ZonedDateTime.now().minusDays(1);

    private UserCouponModel rate10(ZonedDateTime expiredAt, Long minOrderAmount) {
        return new UserCouponModel(USER_ID, TEMPLATE_ID, CouponType.RATE, 10L, minOrderAmount, expiredAt);
    }

    @DisplayName("발급 쿠폰을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("스냅샷 값으로 생성되고, 초기 상태는 AVAILABLE 이다.")
        @Test
        void createsAvailable_withSnapshot() {
            UserCouponModel coupon = rate10(FUTURE, 10000L);

            assertThat(coupon.getUserId()).isEqualTo(USER_ID);
            assertThat(coupon.getTemplateId()).isEqualTo(TEMPLATE_ID);
            assertThat(coupon.getType()).isEqualTo(CouponType.RATE);
            assertThat(coupon.getValue()).isEqualTo(10L);
            assertThat(coupon.getMinOrderAmount()).isEqualTo(10000L);
            assertThat(coupon.getExpiredAt()).isEqualTo(FUTURE);
            assertThat(coupon.getStatus()).isEqualTo(UserCouponStatus.AVAILABLE);
            assertThat(coupon.getOrderId()).isNull();
            assertThat(coupon.getUsedAt()).isNull();
        }
    }

    @DisplayName("표시 상태(displayStatus)를 판정할 때, ")
    @Nested
    class DisplayStatus {

        @DisplayName("사용 안 했고 만료 전이면, AVAILABLE 이다.")
        @Test
        void available_whenNotUsedAndNotExpired() {
            UserCouponModel coupon = rate10(FUTURE, null);

            assertThat(coupon.displayStatus(ZonedDateTime.now())).isEqualTo(UserCouponStatus.AVAILABLE);
        }

        @DisplayName("사용 안 했지만 만료됐으면, EXPIRED 이다.")
        @Test
        void expired_whenNotUsedButExpired() {
            UserCouponModel coupon = rate10(PAST, null);

            assertThat(coupon.displayStatus(ZonedDateTime.now())).isEqualTo(UserCouponStatus.EXPIRED);
        }

        @DisplayName("사용했으면 만료 여부와 무관하게 USED 이다.")
        @Test
        void used_whenUsed() {
            UserCouponModel coupon = rate10(PAST, null);
            coupon.use(UUID.randomUUID());

            assertThat(coupon.displayStatus(ZonedDateTime.now())).isEqualTo(UserCouponStatus.USED);
        }
    }

    @DisplayName("할인액을 계산할 때, ")
    @Nested
    class CalculateDiscount {

        @DisplayName("RATE 는 원금 × 비율을 버림한 값을 반환하고, 원금을 초과하지 않는다.")
        @Test
        void floorsAndCaps() {
            UserCouponModel coupon = rate10(FUTURE, null);

            assertThat(coupon.calculateDiscount(9999L)).isEqualTo(999L);
        }
    }

    @DisplayName("최소 주문 금액 충족 여부를 판정할 때, ")
    @Nested
    class MeetsMinOrderAmount {

        @DisplayName("minOrderAmount 가 null 이면 항상 충족한다.")
        @Test
        void alwaysMeets_whenNull() {
            assertThat(rate10(FUTURE, null).meetsMinOrderAmount(0L)).isTrue();
        }

        @DisplayName("원금이 기준 이상이면 충족, 미만이면 미충족.")
        @Test
        void checksThreshold() {
            UserCouponModel coupon = rate10(FUTURE, 10000L);

            assertThat(coupon.meetsMinOrderAmount(10000L)).isTrue();
            assertThat(coupon.meetsMinOrderAmount(9999L)).isFalse();
        }
    }

    @DisplayName("쿠폰을 사용할 때, ")
    @Nested
    class Use {

        @DisplayName("AVAILABLE 이면 USED 로 전이되고 orderId/usedAt 이 기록된다.")
        @Test
        void transitionsToUsed_whenAvailable() {
            UserCouponModel coupon = rate10(FUTURE, null);
            UUID orderId = UUID.randomUUID();

            coupon.use(orderId);

            assertThat(coupon.getStatus()).isEqualTo(UserCouponStatus.USED);
            assertThat(coupon.getOrderId()).isEqualTo(orderId);
            assertThat(coupon.getUsedAt()).isNotNull();
        }

        @DisplayName("이미 USED 인 쿠폰을 다시 사용하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenAlreadyUsed() {
            UserCouponModel coupon = rate10(FUTURE, null);
            coupon.use(UUID.randomUUID());

            CoreException ex = assertThrows(CoreException.class, () ->
                coupon.use(UUID.randomUUID())
            );

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("쿠폰을 복구(release)할 때, ")
    @Nested
    class Release {

        @DisplayName("USED 이면 AVAILABLE 로 복구되고 orderId/usedAt 이 비워진다.")
        @Test
        void restoresToAvailable_whenUsed() {
            UserCouponModel coupon = rate10(FUTURE, null);
            coupon.use(UUID.randomUUID());

            coupon.release();

            assertThat(coupon.getStatus()).isEqualTo(UserCouponStatus.AVAILABLE);
            assertThat(coupon.getOrderId()).isNull();
            assertThat(coupon.getUsedAt()).isNull();
        }

        @DisplayName("AVAILABLE 인 쿠폰을 release 해도 멱등하게 AVAILABLE 을 유지한다.")
        @Test
        void isIdempotent_whenAvailable() {
            UserCouponModel coupon = rate10(FUTURE, null);

            coupon.release();

            assertThat(coupon.getStatus()).isEqualTo(UserCouponStatus.AVAILABLE);
        }
    }
}
