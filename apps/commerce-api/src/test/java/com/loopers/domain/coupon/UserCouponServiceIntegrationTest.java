package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class UserCouponServiceIntegrationTest {

    @Autowired
    private UserCouponService userCouponService;

    @Autowired
    private CouponService couponService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private LocalDateTime future() {
        return LocalDateTime.of(2999, 12, 31, 23, 59, 59);
    }

    private LocalDateTime past() {
        return LocalDateTime.of(2020, 1, 1, 0, 0);
    }

    @DisplayName("쿠폰을 발급할 때")
    @Nested
    class Issue {

        @DisplayName("정상 발급되어 내 쿠폰 목록에서 조회된다.")
        @Test
        void issues() {
            CouponModel coupon = couponService.createCoupon("A", CouponType.FIXED, 1000L, null, future(), null);

            userCouponService.issue(1L, coupon.getId());

            assertThat(userCouponService.getMyCoupons(1L)).hasSize(1);
        }

        @DisplayName("존재하지 않는 쿠폰이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenCouponMissing() {
            CoreException result = assertThrows(CoreException.class,
                    () -> userCouponService.issue(1L, 99L));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("같은 쿠폰을 이미 발급받았으면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenAlreadyIssued() {
            CouponModel coupon = couponService.createCoupon("A", CouponType.FIXED, 1000L, null, future(), null);
            userCouponService.issue(1L, coupon.getId());

            CoreException result = assertThrows(CoreException.class,
                    () -> userCouponService.issue(1L, coupon.getId()));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("수량이 한정된 쿠폰을 발급하면, 수량이 1만큼 차감된다.")
        @Test
        void decreasesQuantity_whenLimited() {
            CouponModel coupon = couponService.createCoupon("A", CouponType.FIXED, 1000L, null, future(), 3);

            userCouponService.issue(1L, coupon.getId());

            assertThat(couponService.getCoupon(coupon.getId()).getQuantity()).isEqualTo(2);
        }

        @DisplayName("수량이 모두 소진된 쿠폰을 발급하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenSoldOut() {
            CouponModel coupon = couponService.createCoupon("A", CouponType.FIXED, 1000L, null, future(), 0);

            CoreException result = assertThrows(CoreException.class,
                    () -> userCouponService.issue(1L, coupon.getId()));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("수량이 null(무제한)인 쿠폰은 수량 제한 없이 발급된다.")
        @Test
        void issues_whenUnlimited() {
            CouponModel coupon = couponService.createCoupon("A", CouponType.FIXED, 1000L, null, future(), null);

            userCouponService.issue(1L, coupon.getId());

            assertAll(
                    () -> assertThat(userCouponService.getMyCoupons(1L)).hasSize(1),
                    () -> assertThat(couponService.getCoupon(coupon.getId()).getQuantity()).isNull()
            );
        }
    }

    @DisplayName("쿠폰을 사용할 때")
    @Nested
    class Use {

        @DisplayName("정상 사용되면, 할인 금액을 반환하고 쿠폰이 사용 처리된다.")
        @Test
        void usesAndReturnsDiscount() {
            CouponModel coupon = couponService.createCoupon("A", CouponType.RATE, 10L, null, future(), null);
            userCouponService.issue(1L, coupon.getId());

            long discount = userCouponService.use(1L, coupon.getId(), 10000L);

            assertAll(
                    () -> assertThat(discount).isEqualTo(1000L),
                    () -> assertThat(userCouponService.getMyCoupons(1L).get(0).isUsed()).isTrue()
            );
        }

        @DisplayName("보유하지 않은 쿠폰이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenNotOwned() {
            CouponModel coupon = couponService.createCoupon("A", CouponType.FIXED, 1000L, null, future(), null);

            CoreException result = assertThrows(CoreException.class,
                    () -> userCouponService.use(1L, coupon.getId(), 10000L));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("만료된 쿠폰이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenExpired() {
            CouponModel coupon = couponService.createCoupon("A", CouponType.FIXED, 1000L, null, past(), null);
            userCouponService.issue(1L, coupon.getId());

            CoreException result = assertThrows(CoreException.class,
                    () -> userCouponService.use(1L, coupon.getId(), 10000L));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이미 사용된 쿠폰이면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenAlreadyUsed() {
            CouponModel coupon = couponService.createCoupon("A", CouponType.FIXED, 1000L, null, future(), null);
            userCouponService.issue(1L, coupon.getId());
            userCouponService.use(1L, coupon.getId(), 10000L);

            CoreException result = assertThrows(CoreException.class,
                    () -> userCouponService.use(1L, coupon.getId(), 10000L));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("쿠폰 사용을 취소하면")
    @Nested
    class Restore {

        @DisplayName("사용 처리된 쿠폰이 다시 사용 가능 상태가 된다.")
        @Test
        void restores() {
            CouponModel coupon = couponService.createCoupon("A", CouponType.FIXED, 1000L, null, future(), null);
            userCouponService.issue(1L, coupon.getId());
            userCouponService.use(1L, coupon.getId(), 10000L);

            userCouponService.restore(1L, coupon.getId());

            assertThat(userCouponService.getMyCoupons(1L).getFirst().isUsed())
                    .isFalse();
        }
    }
}