package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponStatus;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class UserCouponServiceIntegrationTest {

    @Autowired private UserCouponService userCouponService;
    @Autowired private CouponJpaRepository couponJpaRepository;
    @Autowired private UserCouponJpaRepository userCouponJpaRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private static final Long USER_ID = 1L;
    private static final ZonedDateTime FUTURE = ZonedDateTime.now().plusDays(30);
    private static final ZonedDateTime PAST = ZonedDateTime.now().minusDays(1);

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private CouponModel saveActiveCoupon() {
        return couponJpaRepository.save(new CouponModel("10% 할인", CouponType.RATE, 10, null, FUTURE));
    }

    @DisplayName("issue()를 호출할 때,")
    @Nested
    class Issue {

        @DisplayName("유효한 쿠폰 발급 시 DB에 저장되고 AVAILABLE 상태의 UserCouponInfo가 반환된다.")
        @Test
        void issuesCoupon_whenValidRequest() {
            // arrange
            CouponModel coupon = saveActiveCoupon();

            // act
            UserCouponInfo result = userCouponService.issue(USER_ID, coupon.getId());

            // assert
            assertAll(
                () -> assertThat(result.id()).isNotNull(),
                () -> assertThat(result.userId()).isEqualTo(USER_ID),
                () -> assertThat(result.status()).isEqualTo(UserCouponStatus.AVAILABLE),
                () -> assertThat(userCouponJpaRepository.findById(result.id())).isPresent()
            );
        }

        @DisplayName("존재하지 않는 쿠폰 발급 시 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenCouponDoesNotExist() {
            CoreException result = assertThrows(CoreException.class,
                () -> userCouponService.issue(USER_ID, 999L));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("소프트딜리트된 쿠폰 발급 시 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenCouponIsDeleted() {
            // arrange
            CouponModel coupon = saveActiveCoupon();
            coupon.delete();
            couponJpaRepository.save(coupon);

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> userCouponService.issue(USER_ID, coupon.getId()));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("만료된 쿠폰 발급 시 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCouponIsExpired() {
            // arrange
            CouponModel expiredCoupon = couponJpaRepository.save(
                new CouponModel("만료쿠폰", CouponType.FIXED, 1_000, null, PAST));

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> userCouponService.issue(USER_ID, expiredCoupon.getId()));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("동일 쿠폰을 중복 발급 시도 시 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenAlreadyIssued() {
            // arrange
            CouponModel coupon = saveActiveCoupon();
            userCouponService.issue(USER_ID, coupon.getId());

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> userCouponService.issue(USER_ID, coupon.getId()));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("getMyCoupons()를 호출할 때,")
    @Nested
    class GetMyCoupons {

        @DisplayName("내 쿠폰 목록이 페이지로 반환된다.")
        @Test
        void returnsUserCoupons_whenCouponsExist() {
            // arrange
            CouponModel couponA = saveActiveCoupon();
            CouponModel couponB = couponJpaRepository.save(
                new CouponModel("정액쿠폰", CouponType.FIXED, 5_000, null, FUTURE));
            userCouponService.issue(USER_ID, couponA.getId());
            userCouponService.issue(USER_ID, couponB.getId());

            // act
            Page<UserCouponInfo> result = userCouponService.getMyCoupons(USER_ID, PageRequest.of(0, 20));

            // assert
            assertThat(result.getTotalElements()).isEqualTo(2);
        }

        @DisplayName("사용된 쿠폰은 USED 상태로 반환된다.")
        @Test
        void returnsUsedStatus_whenCouponIsUsed() {
            // arrange
            CouponModel coupon = saveActiveCoupon();
            UserCouponModel userCoupon = userCouponJpaRepository.save(new UserCouponModel(USER_ID, coupon));
            userCoupon.use();
            userCouponJpaRepository.save(userCoupon);

            // act
            Page<UserCouponInfo> result = userCouponService.getMyCoupons(USER_ID, PageRequest.of(0, 20));

            // assert
            assertThat(result.getContent().get(0).status()).isEqualTo(UserCouponStatus.USED);
        }

        @DisplayName("만료된 쿠폰은 EXPIRED 상태로 반환된다.")
        @Test
        void returnsExpiredStatus_whenCouponIsExpired() {
            // arrange — 만료된 쿠폰을 직접 저장 (issue()는 만료 쿠폰을 거부하므로)
            CouponModel expiredCoupon = couponJpaRepository.save(
                new CouponModel("만료쿠폰", CouponType.FIXED, 1_000, null, PAST));
            userCouponJpaRepository.save(new UserCouponModel(USER_ID, expiredCoupon));

            // act
            Page<UserCouponInfo> result = userCouponService.getMyCoupons(USER_ID, PageRequest.of(0, 20));

            // assert
            assertThat(result.getContent().get(0).status()).isEqualTo(UserCouponStatus.EXPIRED);
        }

        @DisplayName("타인의 쿠폰은 포함되지 않는다.")
        @Test
        void excludesOtherUsersCoupons() {
            // arrange
            CouponModel coupon = saveActiveCoupon();
            userCouponService.issue(USER_ID, coupon.getId());

            CouponModel anotherCoupon = couponJpaRepository.save(
                new CouponModel("타인쿠폰", CouponType.FIXED, 1_000, null, FUTURE));
            userCouponService.issue(99L, anotherCoupon.getId());

            // act
            Page<UserCouponInfo> result = userCouponService.getMyCoupons(USER_ID, PageRequest.of(0, 20));

            // assert
            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }
}
