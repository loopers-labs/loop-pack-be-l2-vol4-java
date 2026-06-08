package com.loopers.domain.coupon;

import com.loopers.domain.shared.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDateTime FAR_FUTURE = LocalDateTime.of(2099, 12, 31, 23, 59);
    private static final LocalDateTime PAST = LocalDateTime.of(2024, 1, 1, 0, 0);

    private CouponRepository couponRepository;
    private UserCouponRepository userCouponRepository;
    private CouponService couponService;

    @BeforeEach
    void setUp() {
        couponRepository = new FakeCouponRepository();
        userCouponRepository = new FakeUserCouponRepository();
        couponService = new CouponService(couponRepository, userCouponRepository);
    }

    @DisplayName("쿠폰 발급 시, ")
    @Nested
    class Issue {

        @DisplayName("정상 발급되면 AVAILABLE 상태로 저장된다.")
        @Test
        void issuesSuccessfully() {
            Coupon coupon = couponRepository.save(Coupon.create("10% 할인", CouponType.RATE, 10L, null, FAR_FUTURE));

            UserCoupon issued = couponService.issueCoupon(USER_ID, coupon.getId());

            assertThat(issued.getUserId()).isEqualTo(USER_ID);
            assertThat(issued.getCouponId()).isEqualTo(coupon.getId());
            assertThat(issued.getStatus()).isEqualTo(CouponStatus.AVAILABLE);
        }

        @DisplayName("존재하지 않는 쿠폰이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenCouponDoesNotExist() {
            CoreException result = assertThrows(CoreException.class,
                () -> couponService.issueCoupon(USER_ID, 999L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("만료된 쿠폰을 발급 시도하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCouponIsExpired() {
            Coupon expired = couponRepository.save(Coupon.create("만료 쿠폰", CouponType.FIXED, 1000L, null, PAST));

            CoreException result = assertThrows(CoreException.class,
                () -> couponService.issueCoupon(USER_ID, expired.getId()));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이미 발급받은 쿠폰을 다시 발급 시도하면 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenAlreadyIssued() {
            Coupon coupon = couponRepository.save(Coupon.create("1회 쿠폰", CouponType.FIXED, 1000L, null, FAR_FUTURE));
            couponService.issueCoupon(USER_ID, coupon.getId());

            CoreException result = assertThrows(CoreException.class,
                () -> couponService.issueCoupon(USER_ID, coupon.getId()));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("쿠폰 사용 시, ")
    @Nested
    class Use {

        @DisplayName("정상 사용하면 할인 금액이 반환되고 UserCoupon 이 USED 로 전이된다.")
        @Test
        void usesSuccessfully() {
            Coupon coupon = couponRepository.save(Coupon.create("3천원 할인", CouponType.FIXED, 3000L, null, FAR_FUTURE));
            UserCoupon issued = couponService.issueCoupon(USER_ID, coupon.getId());

            Money discount = couponService.useCoupon(USER_ID, issued.getId(), Money.of(10000L));

            assertThat(discount).isEqualTo(Money.of(3000L));
            assertThat(issued.getStatus()).isEqualTo(CouponStatus.USED);
            assertThat(issued.getUsedAt()).isNotNull();
        }

        @DisplayName("타 유저의 쿠폰으로 사용 시도하면 존재 노출 방지를 위해 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNotOwner() {
            Coupon coupon = couponRepository.save(Coupon.create("쿠폰", CouponType.FIXED, 1000L, null, FAR_FUTURE));
            UserCoupon issued = couponService.issueCoupon(USER_ID, coupon.getId());

            CoreException result = assertThrows(CoreException.class,
                () -> couponService.useCoupon(999L, issued.getId(), Money.of(10000L)));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("이미 사용된 쿠폰을 다시 사용하면 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenAlreadyUsed() {
            Coupon coupon = couponRepository.save(Coupon.create("쿠폰", CouponType.FIXED, 1000L, null, FAR_FUTURE));
            UserCoupon issued = couponService.issueCoupon(USER_ID, coupon.getId());
            couponService.useCoupon(USER_ID, issued.getId(), Money.of(10000L));

            CoreException result = assertThrows(CoreException.class,
                () -> couponService.useCoupon(USER_ID, issued.getId(), Money.of(10000L)));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }
}
