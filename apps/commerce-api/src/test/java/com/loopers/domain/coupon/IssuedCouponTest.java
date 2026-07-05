package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IssuedCouponTest {

    @DisplayName("발급 쿠폰을 사용하면 USED 상태가 되고 다시 사용할 수 없다.")
    @Test
    void usesCouponOnlyOnce() {
        ZonedDateTime now = ZonedDateTime.now();
        IssuedCoupon coupon = issuedCoupon(now.plusDays(1));

        assertThat(coupon.use("user1", 10_000L, now)).isEqualTo(1_000L);
        assertThat(coupon.getStatus(now)).isEqualTo(CouponStatus.USED);
        assertThrows(CoreException.class, () -> coupon.use("user1", 10_000L, now));
    }

    @DisplayName("다른 사용자가 소유한 쿠폰은 사용할 수 없다.")
    @Test
    void rejectsCouponOwnedByAnotherUser() {
        ZonedDateTime now = ZonedDateTime.now();
        IssuedCoupon coupon = issuedCoupon(now.plusDays(1));

        assertThrows(CoreException.class, () -> coupon.use("user2", 10_000L, now));
    }

    @DisplayName("만료된 AVAILABLE 쿠폰은 조회 시 EXPIRED 상태로 해석한다.")
    @Test
    void calculatesExpiredStatus() {
        ZonedDateTime now = ZonedDateTime.now();
        IssuedCoupon coupon = issuedCoupon(now.minusSeconds(1));

        assertThat(coupon.getStatus(now)).isEqualTo(CouponStatus.EXPIRED);
    }

    @DisplayName("사용한 쿠폰을 복구하면 AVAILABLE 상태로 돌아간다.")
    @Test
    void restoresUsedCoupon() {
        ZonedDateTime now = ZonedDateTime.now();
        IssuedCoupon coupon = issuedCoupon(now.plusDays(1));
        coupon.use("user1", 10_000L, now);

        coupon.restore();

        assertThat(coupon.getStatus(now)).isEqualTo(CouponStatus.AVAILABLE);
        assertThat(coupon.getUsedAt()).isNull();
    }

    private IssuedCoupon issuedCoupon(ZonedDateTime expiredAt) {
        CouponTemplate template = CouponTemplate.reconstruct(
            1L,
            "테스트 쿠폰",
            CouponType.FIXED,
            1_000L,
            null,
            null,
            1,
            expiredAt,
            ZonedDateTime.now(),
            ZonedDateTime.now(),
            null
        );
        return new IssuedCoupon(1L, "user1", template);
    }
}
