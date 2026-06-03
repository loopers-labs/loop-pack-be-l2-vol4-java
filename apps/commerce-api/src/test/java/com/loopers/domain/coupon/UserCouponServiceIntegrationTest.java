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

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
public class UserCouponServiceIntegrationTest {

    private static final Long USER_ID = 100L;

    @Autowired CouponService couponService;
    @Autowired UserCouponService userCouponService;
    @Autowired UserCouponRepository userCouponRepository;
    @Autowired DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private CouponModel rateCoupon(long percent, Long minOrderAmount) {
        return couponService.register("할인", CouponType.RATE, percent, minOrderAmount, ZonedDateTime.now().plusDays(7));
    }

    @Nested
    @DisplayName("발급 (issue)")
    class Issue {

        @DisplayName("발급 가능한 템플릿이면 사용 가능한 발급분이 생긴다")
        @Test
        void given_issuable_when_issue_then_available() {
            CouponModel template = rateCoupon(10, null);

            IssuedCouponView view = userCouponService.issue(USER_ID, template.getId());

            assertAll(
                    () -> assertThat(view.status()).isEqualTo(UserCouponStatus.AVAILABLE),
                    () -> assertThat(userCouponRepository.findFirstAvailable(USER_ID, template.getId())).isPresent()
            );
        }

        @DisplayName("존재하지 않는 템플릿으로 발급하면 NOT_FOUND")
        @Test
        void given_missingTemplate_when_issue_then_notFound() {
            Throwable thrown = catchThrowable(() -> userCouponService.issue(USER_ID, 9999L));
            assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("같은 템플릿을 두 번 발급하면 발급분이 2개가 된다 (중복 발급 허용)")
        @Test
        void given_sameTemplate_when_issueTwice_then_two() {
            CouponModel template = rateCoupon(10, null);

            userCouponService.issue(USER_ID, template.getId());
            userCouponService.issue(USER_ID, template.getId());

            assertThat(userCouponService.getMyCoupons(USER_ID, 0, 20)).hasSize(2);
        }
    }

    @Nested
    @DisplayName("주문 적용 (useForOrder)")
    class UseForOrder {

        @DisplayName("정상: 할인 금액을 반환하고 발급분은 사용 완료가 된다")
        @Test
        void given_available_when_use_then_usedAndDiscount() {
            CouponModel template = rateCoupon(10, 5000L);
            userCouponService.issue(USER_ID, template.getId());

            AppliedCoupon applied = userCouponService.useForOrder(USER_ID, template.getId(), 10000L);

            assertAll(
                    () -> assertThat(applied.discountAmount()).isEqualTo(1000L),
                    () -> assertThat(userCouponRepository.findFirstAvailable(USER_ID, template.getId())).isEmpty()
            );
        }

        @DisplayName("사용 가능 발급분이 없으면 NOT_FOUND")
        @Test
        void given_noneAvailable_when_use_then_notFound() {
            CouponModel template = rateCoupon(10, null);

            Throwable thrown = catchThrowable(() -> userCouponService.useForOrder(USER_ID, template.getId(), 10000L));
            assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("최소 주문 금액 미달이면 BAD_REQUEST이고 발급분은 사용되지 않는다")
        @Test
        void given_belowMin_when_use_then_badRequestAndNotUsed() {
            CouponModel template = rateCoupon(10, 10000L);
            userCouponService.issue(USER_ID, template.getId());

            Throwable thrown = catchThrowable(() -> userCouponService.useForOrder(USER_ID, template.getId(), 9999L));

            assertAll(
                    () -> assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
                    () -> assertThat(userCouponRepository.findFirstAvailable(USER_ID, template.getId())).isPresent()
            );
        }
    }

    @Nested
    @DisplayName("원복 (restore)")
    class Restore {

        @DisplayName("사용된 발급분을 원복하면 다시 사용 가능해진다")
        @Test
        void given_used_when_restore_then_availableAgain() {
            CouponModel template = rateCoupon(10, null);
            userCouponService.issue(USER_ID, template.getId());
            AppliedCoupon applied = userCouponService.useForOrder(USER_ID, template.getId(), 10000L);

            userCouponService.restore(applied.userCouponId());

            assertThat(userCouponRepository.findFirstAvailable(USER_ID, template.getId())).isPresent();
        }
    }

    @Nested
    @DisplayName("내 쿠폰 목록 (getMyCoupons)")
    class GetMyCoupons {

        @DisplayName("사용/만료/사용가능 상태가 함께 반환된다")
        @Test
        void given_variousCoupons_when_getMyCoupons_then_statuses() {
            CouponModel usable = rateCoupon(10, null);
            userCouponService.issue(USER_ID, usable.getId());

            List<IssuedCouponView> views = userCouponService.getMyCoupons(USER_ID, 0, 20);

            assertThat(views).hasSize(1);
            assertThat(views.get(0).status()).isEqualTo(UserCouponStatus.AVAILABLE);
        }
    }
}
