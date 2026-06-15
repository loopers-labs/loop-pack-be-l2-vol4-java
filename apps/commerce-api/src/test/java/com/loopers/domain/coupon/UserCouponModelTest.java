package com.loopers.domain.coupon;

import com.loopers.domain.vo.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserCouponModelTest {

    private static final ZonedDateTime NOW =
            ZonedDateTime.of(2026, 6, 10, 0, 0, 0, 0, ZoneId.of("Asia/Seoul"));
    private static final DiscountPolicy POLICY =
            DiscountPolicy.of(DiscountType.RATE, 10, Money.of(0));

    @Test
    void marksUsed_whenAvailableCouponIsUsed() {
        // arrange — 내일 만료, 사용 가능 쿠폰
        UserCouponModel coupon = UserCouponModel.issue(1L, 100L, POLICY, NOW.plusDays(1));

        // act
        coupon.use(NOW);

        // assert
        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.USED);
    }

    @Test
    void throwsConflict_whenAlreadyUsedCouponIsUsedAgain() {
        // arrange — 이미 사용한 쿠폰
        UserCouponModel coupon = UserCouponModel.issue(1L, 100L, POLICY, NOW.plusDays(1));
        coupon.use(NOW);

        // act
        CoreException result = assertThrows(CoreException.class, () -> coupon.use(NOW));

        // assert
        assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
    }

    @Test
    void throwsBadRequest_whenExpiredCouponIsUsed() {
        // arrange — 어제 이미 만료된 쿠폰
        UserCouponModel coupon = UserCouponModel.issue(1L, 100L, POLICY, NOW.minusDays(1));

        // act
        CoreException result = assertThrows(CoreException.class, () -> coupon.use(NOW));

        // assert
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }
}
