package com.loopers.coupon.application;

import com.loopers.coupon.domain.Coupon;
import com.loopers.coupon.domain.CouponErrorCode;
import com.loopers.coupon.domain.CouponRepository;
import com.loopers.coupon.domain.CouponStatus;
import com.loopers.coupon.domain.CouponType;
import com.loopers.coupon.domain.UserCoupon;
import com.loopers.coupon.domain.UserCouponRepository;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CouponIssueServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long COUPON_ID = 10L;
    private static final ZonedDateTime EXPIRES = ZonedDateTime.parse("2030-12-31T23:59:59+09:00");

    private final CouponRepository couponRepository = mock(CouponRepository.class);
    private final UserCouponRepository userCouponRepository = mock(UserCouponRepository.class);
    private final CouponIssueService couponIssueService = new CouponIssueService(couponRepository, userCouponRepository);

    private Coupon template() {
        return Coupon.create("신규가입 3천원", CouponType.FIXED, 3_000L, 10_000L, EXPIRES);
    }

    @Test
    @DisplayName("발급에 성공하면 소유자·정책 스냅샷을 가진 AVAILABLE 쿠폰을 저장한다")
    void givenIssuableTemplate_whenIssue_thenSavesAvailableUserCoupon() {
        when(couponRepository.findById(COUPON_ID)).thenReturn(Optional.of(template()));
        when(userCouponRepository.existsByCouponIdAndUserId(COUPON_ID, USER_ID)).thenReturn(false);
        when(userCouponRepository.save(any(UserCoupon.class))).thenAnswer(inv -> inv.getArgument(0));

        couponIssueService.issue(USER_ID, COUPON_ID);

        ArgumentCaptor<UserCoupon> captor = ArgumentCaptor.forClass(UserCoupon.class);
        verify(userCouponRepository).save(captor.capture());
        UserCoupon saved = captor.getValue();
        assertAll(
                () -> assertThat(saved.getUserId()).isEqualTo(USER_ID),
                () -> assertThat(saved.getStatus()).isEqualTo(CouponStatus.AVAILABLE),
                () -> assertThat(saved.getType()).isEqualTo(CouponType.FIXED),
                () -> assertThat(saved.getValue()).isEqualTo(3_000L),
                () -> assertThat(saved.getExpiredAt()).isEqualTo(EXPIRES)
        );
    }

    @Test
    @DisplayName("템플릿이 없으면 COUPON_NOT_FOUND 가 발생한다")
    void givenMissingTemplate_whenIssue_thenThrowsNotFound() {
        when(couponRepository.findById(COUPON_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponIssueService.issue(USER_ID, COUPON_ID))
                .isInstanceOf(CoreException.class)
                .extracting("errorCode")
                .isEqualTo(CouponErrorCode.COUPON_NOT_FOUND);
        verify(userCouponRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 발급받은 템플릿이면 COUPON_ALREADY_ISSUED 가 발생하고 저장하지 않는다")
    void givenAlreadyIssued_whenIssue_thenThrowsConflict() {
        when(couponRepository.findById(COUPON_ID)).thenReturn(Optional.of(template()));
        when(userCouponRepository.existsByCouponIdAndUserId(COUPON_ID, USER_ID)).thenReturn(true);

        assertThatThrownBy(() -> couponIssueService.issue(USER_ID, COUPON_ID))
                .isInstanceOf(CoreException.class)
                .extracting("errorCode")
                .isEqualTo(CouponErrorCode.COUPON_ALREADY_ISSUED);
        verify(userCouponRepository, never()).save(any());
    }

    @Test
    @DisplayName("동시 발급 경합으로 unique 제약을 위반하면 COUPON_ALREADY_ISSUED 로 변환한다")
    void givenUniqueViolationRace_whenIssue_thenThrowsConflict() {
        when(couponRepository.findById(COUPON_ID)).thenReturn(Optional.of(template()));
        when(userCouponRepository.existsByCouponIdAndUserId(COUPON_ID, USER_ID)).thenReturn(false);
        when(userCouponRepository.save(any(UserCoupon.class)))
                .thenThrow(new DataIntegrityViolationException("uk_user_coupon_coupon_user"));

        assertThatThrownBy(() -> couponIssueService.issue(USER_ID, COUPON_ID))
                .isInstanceOf(CoreException.class)
                .extracting("errorCode")
                .isEqualTo(CouponErrorCode.COUPON_ALREADY_ISSUED);
    }
}
