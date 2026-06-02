package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long TEMPLATE_ID = 100L;

    @Mock
    private CouponTemplateRepository couponTemplateRepository;

    @Mock
    private IssuedCouponRepository issuedCouponRepository;

    @InjectMocks
    private CouponService couponService;

    private CouponTemplate template() {
        return new CouponTemplate("쿠폰", CouponType.FIXED, 3_000L, null, ZonedDateTime.now().plusDays(7));
    }

    @DisplayName("쿠폰 발급 시")
    @Nested
    class Issue {

        @DisplayName("템플릿이 존재하고 미발급 상태이면 AVAILABLE 쿠폰이 저장된다")
        @Test
        void savesIssuedCoupon_whenTemplateExistsAndNotIssued() {
            // given
            when(couponTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template()));
            when(issuedCouponRepository.existsByUserIdAndCouponTemplateId(USER_ID, TEMPLATE_ID)).thenReturn(false);
            when(issuedCouponRepository.save(any(IssuedCoupon.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            IssuedCoupon issued = couponService.issue(USER_ID, TEMPLATE_ID);

            // then
            assertThat(issued.getStatus()).isEqualTo(CouponStatus.AVAILABLE);
            verify(issuedCouponRepository).save(any(IssuedCoupon.class));
        }

        @DisplayName("템플릿이 존재하지 않으면 NOT_FOUND 예외가 발생하고 저장하지 않는다")
        @Test
        void throwsNotFound_whenTemplateDoesNotExist() {
            // given
            when(couponTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.empty());

            // when
            CoreException ex = assertThrows(CoreException.class, () -> couponService.issue(USER_ID, TEMPLATE_ID));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verify(issuedCouponRepository, never()).save(any(IssuedCoupon.class));
        }

        @DisplayName("이미 발급받은 쿠폰이면 CONFLICT 예외가 발생하고 저장하지 않는다")
        @Test
        void throwsConflict_whenAlreadyIssued() {
            // given
            when(couponTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template()));
            when(issuedCouponRepository.existsByUserIdAndCouponTemplateId(USER_ID, TEMPLATE_ID)).thenReturn(true);

            // when
            CoreException ex = assertThrows(CoreException.class, () -> couponService.issue(USER_ID, TEMPLATE_ID));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
            verify(issuedCouponRepository, never()).save(any(IssuedCoupon.class));
        }
    }
}
