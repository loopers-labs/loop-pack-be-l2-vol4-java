package com.loopers.domain.coupon;

import com.loopers.domain.coupon.enums.CouponType;
import com.loopers.domain.coupon.enums.UserCouponStatus;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class UserCouponServiceIntegrationTest {

    @Autowired private UserCouponService sut;
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

    private CouponModel saveValidCoupon() {
        return couponJpaRepository.save(new CouponModel(
                "테스트 쿠폰",
                new CouponDiscount(CouponType.RATE, 10L, null),
                new CouponExpiry(FUTURE)
        ));
    }

    private CouponModel saveExpiredCoupon() {
        return couponJpaRepository.save(new CouponModel(
                "만료 쿠폰",
                new CouponDiscount(CouponType.RATE, 10L, null),
                new CouponExpiry(PAST)
        ));
    }

    @DisplayName("쿠폰 발급 시,")
    @Nested
    class Issue {

        @DisplayName("유효한 쿠폰이면, 발급된다.")
        @Test
        void returnsUserCoupon_whenCouponIsValid() {
            CouponModel coupon = saveValidCoupon();

            UserCouponModel result = sut.issue(coupon.getId(), USER_ID);

            assertThat(result.getUserId()).isEqualTo(USER_ID);
            assertThat(result.getStatus()).isEqualTo(UserCouponStatus.ISSUED);
        }

        @DisplayName("만료된 쿠폰이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCouponIsExpired() {
            CouponModel coupon = saveExpiredCoupon();

            CoreException exception = assertThrows(CoreException.class,
                    () -> sut.issue(coupon.getId(), USER_ID));

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

    }

    @DisplayName("내 쿠폰 목록 조회 시,")
    @Nested
    class GetMyList {

        @DisplayName("발급된 쿠폰 목록을 반환한다.")
        @Test
        void returnsIssuedCoupons_whenUserHasCoupons() {
            CouponModel coupon = saveValidCoupon();
            sut.issue(coupon.getId(), USER_ID);

            List<UserCouponModel> result = sut.getMyList(USER_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUserId()).isEqualTo(USER_ID);
        }

        @DisplayName("발급된 쿠폰이 없으면, 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenUserHasNoCoupons() {
            List<UserCouponModel> result = sut.getMyList(USER_ID);

            assertThat(result).isEmpty();
        }
    }

    @DisplayName("쿠폰 사용(use) 시,")
    @Nested
    class Use {

        @DisplayName("유효한 쿠폰이면, USED 상태로 변경되고 할인 금액이 반환된다.")
        @Test
        void returnsUseResult_whenCouponIsValid() {
            CouponModel coupon = saveValidCoupon();
            UserCouponModel userCoupon = sut.issue(coupon.getId(), USER_ID);

            UserCouponService.UseResult result = sut.use(userCoupon.getId(), USER_ID, 10000L);

            assertThat(result.discountAmount()).isPositive();
            assertThat(sut.get(userCoupon.getId()).getStatus()).isEqualTo(UserCouponStatus.USED);
        }

        @DisplayName("userCouponId가 null이면, 할인 금액 0으로 반환된다.")
        @Test
        void returnsZeroDiscount_whenUserCouponIdIsNull() {
            UserCouponService.UseResult result = sut.use(null, USER_ID, 10000L);

            assertThat(result.originalAmount()).isEqualTo(10000L);
            assertThat(result.discountAmount()).isZero();
        }
    }

    @DisplayName("쿠폰 복구(revert) 시,")
    @Nested
    class Revert {

        @DisplayName("사용된 쿠폰이면, ISSUED 상태로 복구된다.")
        @Test
        void revertsToIssued_whenCouponIsUsed() {
            CouponModel coupon = saveValidCoupon();
            UserCouponModel userCoupon = sut.issue(coupon.getId(), USER_ID);
            userCoupon.use();
            userCouponJpaRepository.save(userCoupon);

            sut.revert(userCoupon.getId());

            UserCouponModel result = sut.get(userCoupon.getId());
            assertThat(result.getStatus()).isEqualTo(UserCouponStatus.ISSUED);
            assertThat(result.getUsedAt()).isNull();
        }

        @DisplayName("userCouponId가 null이면, 아무 동작도 하지 않는다.")
        @Test
        void doesNothing_whenUserCouponIdIsNull() {
            sut.revert(null);
        }
    }

    @DisplayName("쿠폰별 발급 내역 조회 시,")
    @Nested
    class GetListByCouponId {

        @DisplayName("발급 내역이 있으면, 페이지 목록을 반환한다.")
        @Test
        void returnsPage_whenIssuesExist() {
            CouponModel coupon = saveValidCoupon();
            sut.issue(coupon.getId(), USER_ID);

            Page<UserCouponModel> result = sut.getListByCouponId(coupon.getId(), PageRequest.of(0, 20));

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getUserId()).isEqualTo(USER_ID);
        }

        @DisplayName("발급 내역이 없으면, 빈 페이지를 반환한다.")
        @Test
        void returnsEmptyPage_whenNoIssuesExist() {
            CouponModel coupon = saveValidCoupon();

            Page<UserCouponModel> result = sut.getListByCouponId(coupon.getId(), PageRequest.of(0, 20));

            assertThat(result.getTotalElements()).isZero();
        }
    }
}
