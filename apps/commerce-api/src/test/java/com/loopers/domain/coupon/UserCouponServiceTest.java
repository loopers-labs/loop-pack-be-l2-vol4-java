package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserCouponService 순수 단위 테스트 — Repository/CouponService를 mock으로 격리해
 * 발급, 주문 적용(useForOrder), 원복 흐름을 DB 없이 검증한다.
 */
class UserCouponServiceTest {

    private static final Long USER_ID = 100L;
    private static final Long COUPON_ID = 42L;

    private UserCouponRepository userCouponRepository;
    private CouponService couponService;
    private UserCouponService userCouponService;

    @BeforeEach
    void setUp() {
        userCouponRepository = mock(UserCouponRepository.class);
        couponService = mock(CouponService.class);
        userCouponService = new UserCouponService(userCouponRepository, couponService);
    }

    private static UserCouponModel availableUserCoupon(Long id) {
        return UserCouponModel.reconstitute(id, USER_ID, COUPON_ID, null, ZonedDateTime.now().minusDays(1));
    }

    private static CouponModel rateCoupon(Long minOrderAmount) {
        return CouponModel.reconstitute(COUPON_ID, "10% 할인", CouponType.RATE, 10L, minOrderAmount,
                ZonedDateTime.now().plusDays(1), null);
    }

    @Nested
    @DisplayName("발급 (issue)")
    class Issue {

        @DisplayName("발급 가능한 템플릿이면 새 발급분을 저장하고 AVAILABLE 뷰를 반환한다")
        @Test
        void given_issuable_when_issue_then_saves() {
            when(couponService.getIssuableTemplate(eq(COUPON_ID), any(ZonedDateTime.class))).thenReturn(rateCoupon(null));
            when(userCouponRepository.save(any(UserCouponModel.class))).thenAnswer(inv -> inv.getArgument(0));

            IssuedCouponView view = userCouponService.issue(USER_ID, COUPON_ID);

            assertThat(view.couponId()).isEqualTo(COUPON_ID);
            assertThat(view.status()).isEqualTo(UserCouponStatus.AVAILABLE);
            verify(userCouponRepository).save(any(UserCouponModel.class));
        }
    }

    @Nested
    @DisplayName("주문 적용 (useForOrder)")
    class UseForOrder {

        @DisplayName("정상: 사용 가능 발급분을 사용 처리하고 할인 금액을 반환한다")
        @Test
        void given_available_when_use_then_appliedDiscount() {
            UserCouponModel uc = availableUserCoupon(7L);
            when(userCouponRepository.findFirstAvailableForUpdate(USER_ID, COUPON_ID)).thenReturn(Optional.of(uc));
            when(couponService.getActiveTemplate(COUPON_ID)).thenReturn(rateCoupon(null));
            when(userCouponRepository.save(any(UserCouponModel.class))).thenAnswer(inv -> inv.getArgument(0));

            AppliedCoupon result = userCouponService.useForOrder(USER_ID, COUPON_ID, 10000L);

            assertThat(result.userCouponId()).isEqualTo(7L);
            assertThat(result.discountAmount()).isEqualTo(1000L);  // 10% of 10000
            assertThat(uc.isUsed()).isTrue();
            verify(userCouponRepository).save(uc);
        }

        @DisplayName("사용 가능 발급분이 없으면 NOT_FOUND, 저장하지 않는다 (§2 격리)")
        @Test
        void given_noneAvailable_when_use_then_notFound() {
            when(userCouponRepository.findFirstAvailableForUpdate(USER_ID, COUPON_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userCouponService.useForOrder(USER_ID, COUPON_ID, 10000L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.NOT_FOUND);
            verify(userCouponRepository, never()).save(any());
        }

        @DisplayName("최소 주문 금액 미달이면 BAD_REQUEST, 사용 처리하지 않는다")
        @Test
        void given_belowMinOrderAmount_when_use_then_badRequest() {
            UserCouponModel uc = availableUserCoupon(7L);
            when(userCouponRepository.findFirstAvailableForUpdate(USER_ID, COUPON_ID)).thenReturn(Optional.of(uc));
            when(couponService.getActiveTemplate(COUPON_ID)).thenReturn(rateCoupon(10000L));

            assertThatThrownBy(() -> userCouponService.useForOrder(USER_ID, COUPON_ID, 9999L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.BAD_REQUEST);
            assertThat(uc.isUsed()).isFalse();
            verify(userCouponRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("원복 (restore)")
    class Restore {

        @DisplayName("존재하면 미사용으로 원복 후 저장한다")
        @Test
        void given_used_when_restore_then_savesRestored() {
            UserCouponModel uc = UserCouponModel.reconstitute(7L, USER_ID, COUPON_ID,
                    ZonedDateTime.now(), ZonedDateTime.now().minusDays(1));
            when(userCouponRepository.find(7L)).thenReturn(Optional.of(uc));
            when(userCouponRepository.save(any(UserCouponModel.class))).thenAnswer(inv -> inv.getArgument(0));

            userCouponService.restore(7L);

            assertThat(uc.isUsed()).isFalse();
            verify(userCouponRepository).save(uc);
        }

        @DisplayName("부재면 아무것도 하지 않는다 (멱등)")
        @Test
        void given_missing_when_restore_then_noop() {
            when(userCouponRepository.find(7L)).thenReturn(Optional.empty());

            userCouponService.restore(7L);

            verify(userCouponRepository, never()).save(any());
        }
    }
}
