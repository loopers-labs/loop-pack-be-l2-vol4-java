package com.loopers.coupon.application;

import com.loopers.common.domain.Money;
import com.loopers.coupon.domain.Coupon;
import com.loopers.coupon.domain.CouponErrorCode;
import com.loopers.coupon.domain.CouponStatus;
import com.loopers.coupon.domain.CouponType;
import com.loopers.coupon.domain.UserCoupon;
import com.loopers.coupon.domain.UserCouponRepository;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CouponUsageServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long USER_COUPON_ID = 50L;

    private final UserCouponRepository userCouponRepository = mock(UserCouponRepository.class);
    private final CouponUsageService couponUsageService = new CouponUsageService(userCouponRepository);

    private UserCoupon issued(CouponType type, long value, Long minOrderAmount) {
        return Coupon.create("쿠폰", type, value, minOrderAmount, ZonedDateTime.now().plusDays(30)).issueTo(USER_ID);
    }

    @Test
    @DisplayName("use 는 비관적 락으로 쿠폰을 조회해 USED 로 전이하고 할인액을 반환한다")
    void givenAvailableCoupon_whenUse_thenTransitionsAndReturnsDiscount() {
        UserCoupon coupon = issued(CouponType.FIXED, 3_000L, null);
        when(userCouponRepository.findByIdForUpdate(USER_COUPON_ID)).thenReturn(Optional.of(coupon));

        Money discount = couponUsageService.use(USER_COUPON_ID, USER_ID, 10_000L);

        assertAll(
                () -> assertThat(coupon.getStatus()).isEqualTo(CouponStatus.USED),
                () -> assertThat(discount.value()).isEqualTo(3_000L)
        );
    }

    @Test
    @DisplayName("use 시 쿠폰이 없으면 COUPON_NOT_FOUND 가 발생한다")
    void givenMissingCoupon_whenUse_thenThrowsNotFound() {
        when(userCouponRepository.findByIdForUpdate(USER_COUPON_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponUsageService.use(USER_COUPON_ID, USER_ID, 10_000L))
                .isInstanceOf(CoreException.class)
                .extracting("errorCode")
                .isEqualTo(CouponErrorCode.COUPON_NOT_FOUND);
    }

    @Test
    @DisplayName("use 시 도메인 검증 실패(만료 등)는 그대로 전파된다")
    void givenExpiredCoupon_whenUse_thenPropagatesDomainError() {
        UserCoupon expired = Coupon.create("쿠폰", CouponType.FIXED, 3_000L, null, ZonedDateTime.now().minusDays(1))
                .issueTo(USER_ID);
        when(userCouponRepository.findByIdForUpdate(USER_COUPON_ID)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> couponUsageService.use(USER_COUPON_ID, USER_ID, 10_000L))
                .isInstanceOf(CoreException.class)
                .extracting("errorCode")
                .isEqualTo(CouponErrorCode.COUPON_EXPIRED);
    }
}
