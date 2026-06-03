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
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CouponService 순수 단위 테스트 — Repository를 mock으로 격리해
 * 발급 가능/활성 검증, 수정/삭제 흐름을 DB 없이 검증한다.
 */
class CouponServiceTest {

    private static final Long COUPON_ID = 42L;

    private CouponRepository couponRepository;
    private CouponService couponService;

    @BeforeEach
    void setUp() {
        couponRepository = mock(CouponRepository.class);
        couponService = new CouponService(couponRepository);
    }

    private static CouponModel coupon(Long id, ZonedDateTime expiredAt, ZonedDateTime deletedAt) {
        return CouponModel.reconstitute(id, "10% 할인", CouponType.RATE, 10L, 5000L, expiredAt, deletedAt);
    }

    @Nested
    @DisplayName("발급 가능 템플릿 조회 (getIssuableTemplate)")
    class GetIssuable {

        @DisplayName("활성 + 미만료면 템플릿을 반환한다")
        @Test
        void given_activeNotExpired_when_getIssuable_then_returns() {
            ZonedDateTime now = ZonedDateTime.now();
            when(couponRepository.find(COUPON_ID)).thenReturn(Optional.of(coupon(COUPON_ID, now.plusDays(1), null)));

            assertThat(couponService.getIssuableTemplate(COUPON_ID, now).getId()).isEqualTo(COUPON_ID);
        }

        @DisplayName("존재하지 않으면 NOT_FOUND")
        @Test
        void given_missing_when_getIssuable_then_notFound() {
            when(couponRepository.find(COUPON_ID)).thenReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> couponService.getIssuableTemplate(COUPON_ID, ZonedDateTime.now()));

            assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("비활성(soft delete)이면 BAD_REQUEST")
        @Test
        void given_inactive_when_getIssuable_then_badRequest() {
            ZonedDateTime now = ZonedDateTime.now();
            when(couponRepository.find(COUPON_ID)).thenReturn(Optional.of(coupon(COUPON_ID, now.plusDays(1), now)));

            Throwable thrown = catchThrowable(() -> couponService.getIssuableTemplate(COUPON_ID, now));

            assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("만료됐으면 BAD_REQUEST")
        @Test
        void given_expired_when_getIssuable_then_badRequest() {
            ZonedDateTime now = ZonedDateTime.now();
            when(couponRepository.find(COUPON_ID)).thenReturn(Optional.of(coupon(COUPON_ID, now.minusSeconds(1), null)));

            Throwable thrown = catchThrowable(() -> couponService.getIssuableTemplate(COUPON_ID, now));

            assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("템플릿 수정/삭제")
    class UpdateAndDelete {

        @DisplayName("활성 템플릿이면 수정 후 저장한다")
        @Test
        void given_active_when_update_then_saves() {
            ZonedDateTime now = ZonedDateTime.now();
            CouponModel coupon = coupon(COUPON_ID, now.plusDays(1), null);
            when(couponRepository.find(COUPON_ID)).thenReturn(Optional.of(coupon));
            when(couponRepository.save(any(CouponModel.class))).thenAnswer(inv -> inv.getArgument(0));

            CouponModel result = couponService.update(COUPON_ID, "20% 할인", 20L, 10000L, now.plusDays(2));

            assertThat(result.getName()).isEqualTo("20% 할인");
            assertThat(result.getValue()).isEqualTo(20L);
            verify(couponRepository).save(coupon);
        }

        @DisplayName("비활성 템플릿 수정은 NOT_FOUND, 저장하지 않는다")
        @Test
        void given_inactive_when_update_then_notFound() {
            ZonedDateTime now = ZonedDateTime.now();
            when(couponRepository.find(COUPON_ID)).thenReturn(Optional.of(coupon(COUPON_ID, now.plusDays(1), now)));

            Throwable thrown = catchThrowable(() -> couponService.update(COUPON_ID, "x", 5L, null, now.plusDays(1)));

            assertThat(((CoreException) thrown).getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verify(couponRepository, never()).save(any());
        }

        @DisplayName("삭제하면 soft delete 후 저장한다")
        @Test
        void given_existing_when_delete_then_savesInactive() {
            ZonedDateTime now = ZonedDateTime.now();
            CouponModel coupon = coupon(COUPON_ID, now.plusDays(1), null);
            when(couponRepository.find(COUPON_ID)).thenReturn(Optional.of(coupon));
            when(couponRepository.save(any(CouponModel.class))).thenAnswer(inv -> inv.getArgument(0));

            couponService.deleteCoupon(COUPON_ID);

            assertThat(coupon.isActive()).isFalse();
            verify(couponRepository).save(coupon);
        }
    }
}
