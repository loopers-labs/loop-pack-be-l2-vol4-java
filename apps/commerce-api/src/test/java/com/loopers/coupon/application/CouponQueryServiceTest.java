package com.loopers.coupon.application;

import com.loopers.coupon.domain.Coupon;
import com.loopers.coupon.domain.CouponStatus;
import com.loopers.coupon.domain.CouponType;
import com.loopers.coupon.domain.UserCoupon;
import com.loopers.coupon.domain.UserCouponRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CouponQueryServiceTest {

    private static final Long USER_ID = 1L;
    private static final ZonedDateTime FUTURE = ZonedDateTime.now().plusDays(30);
    private static final ZonedDateTime PAST = ZonedDateTime.now().minusDays(1);

    private final UserCouponRepository userCouponRepository = mock(UserCouponRepository.class);
    private final CouponQueryService couponQueryService = new CouponQueryService(userCouponRepository);

    private UserCoupon issued(ZonedDateTime expiredAt) {
        return Coupon.create("쿠폰", CouponType.FIXED, 3_000L, null, expiredAt).issueTo(USER_ID);
    }

    @Test
    @DisplayName("내 쿠폰 목록을 조회하면 보유 쿠폰을 모두 반환한다")
    void givenUserCoupons_whenGetMyCoupons_thenReturnsAll() {
        when(userCouponRepository.findByUserId(USER_ID))
                .thenReturn(List.of(issued(FUTURE), issued(FUTURE)));

        CouponResult.MyCoupons result = couponQueryService.getMyCoupons(USER_ID);

        assertThat(result.coupons()).hasSize(2);
    }

    @Test
    @DisplayName("만료된 보유 쿠폰은 EXPIRED 파생 상태로 반환한다")
    void givenExpiredCoupon_whenGetMyCoupons_thenStatusIsExpired() {
        when(userCouponRepository.findByUserId(USER_ID))
                .thenReturn(List.of(issued(PAST)));

        CouponResult.MyCoupons result = couponQueryService.getMyCoupons(USER_ID);

        assertThat(result.coupons().get(0).status()).isEqualTo(CouponStatus.EXPIRED);
    }

    @Test
    @DisplayName("유효한 보유 쿠폰은 AVAILABLE 상태로 반환한다")
    void givenValidCoupon_whenGetMyCoupons_thenStatusIsAvailable() {
        when(userCouponRepository.findByUserId(USER_ID))
                .thenReturn(List.of(issued(FUTURE)));

        CouponResult.MyCoupons result = couponQueryService.getMyCoupons(USER_ID);

        assertThat(result.coupons().get(0).status()).isEqualTo(CouponStatus.AVAILABLE);
    }
}
