package com.loopers.domain.coupon;

import com.loopers.domain.common.Money;
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
    private static final Long COUPON_ID = 500L;

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

        @DisplayName("템플릿이 존재하면 AVAILABLE 쿠폰이 저장된다")
        @Test
        void savesIssuedCoupon_whenTemplateExists() {
            // given
            when(couponTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template()));
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

        @DisplayName("이미 만료된 템플릿으로 발급하면 CONFLICT 예외가 발생하고 저장하지 않는다")
        @Test
        void throwsConflict_whenTemplateExpired() {
            // given
            CouponTemplate expired = new CouponTemplate("만료쿠폰", CouponType.FIXED, 3_000L, null, ZonedDateTime.now().minusDays(1));
            when(couponTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(expired));

            // when
            CoreException ex = assertThrows(CoreException.class, () -> couponService.issue(USER_ID, TEMPLATE_ID));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
            verify(issuedCouponRepository, never()).save(any(IssuedCoupon.class));
        }
    }

    @DisplayName("쿠폰 사용 시")
    @Nested
    class Use {

        @DisplayName("소유·미만료·최소금액 충족·AVAILABLE이면 USED로 전이되고 할인액을 반환한다")
        @Test
        void usesAndReturnsDiscount_whenAllConditionsMet() {
            // given
            IssuedCoupon coupon = new IssuedCoupon(USER_ID, TEMPLATE_ID);
            when(issuedCouponRepository.findById(COUPON_ID)).thenReturn(Optional.of(coupon));
            when(couponTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template()));

            // when
            Money discount = couponService.use(USER_ID, COUPON_ID, Money.of(10_000L));

            // then
            assertThat(discount).isEqualTo(Money.of(3_000L));
            assertThat(coupon.getStatus()).isEqualTo(CouponStatus.USED);
        }

        @DisplayName("쿠폰이 존재하지 않으면 NOT_FOUND 예외가 발생한다")
        @Test
        void throwsNotFound_whenCouponDoesNotExist() {
            // given
            when(issuedCouponRepository.findById(COUPON_ID)).thenReturn(Optional.empty());

            // when
            CoreException ex = assertThrows(CoreException.class,
                () -> couponService.use(USER_ID, COUPON_ID, Money.of(10_000L)));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("타 유저 소유 쿠폰이면 NOT_FOUND 예외가 발생한다")
        @Test
        void throwsNotFound_whenNotOwner() {
            // given
            IssuedCoupon othersCoupon = new IssuedCoupon(999L, TEMPLATE_ID);
            when(issuedCouponRepository.findById(COUPON_ID)).thenReturn(Optional.of(othersCoupon));

            // when
            CoreException ex = assertThrows(CoreException.class,
                () -> couponService.use(USER_ID, COUPON_ID, Money.of(10_000L)));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("만료된 쿠폰이면 CONFLICT 예외가 발생하고 사용 처리되지 않는다")
        @Test
        void throwsConflict_whenExpired() {
            // given
            IssuedCoupon coupon = new IssuedCoupon(USER_ID, TEMPLATE_ID);
            CouponTemplate expired = new CouponTemplate("만료쿠폰", CouponType.FIXED, 3_000L, null, ZonedDateTime.now().minusDays(1));
            when(issuedCouponRepository.findById(COUPON_ID)).thenReturn(Optional.of(coupon));
            when(couponTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(expired));

            // when
            CoreException ex = assertThrows(CoreException.class,
                () -> couponService.use(USER_ID, COUPON_ID, Money.of(10_000L)));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
            assertThat(coupon.getStatus()).isEqualTo(CouponStatus.AVAILABLE);
        }

        @DisplayName("최소 주문 금액 미달이면 CONFLICT 예외가 발생하고 사용 처리되지 않는다")
        @Test
        void throwsConflict_whenBelowMinOrderAmount() {
            // given
            IssuedCoupon coupon = new IssuedCoupon(USER_ID, TEMPLATE_ID);
            CouponTemplate minTemplate = new CouponTemplate("최소1만", CouponType.FIXED, 3_000L, 10_000L, ZonedDateTime.now().plusDays(7));
            when(issuedCouponRepository.findById(COUPON_ID)).thenReturn(Optional.of(coupon));
            when(couponTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(minTemplate));

            // when
            CoreException ex = assertThrows(CoreException.class,
                () -> couponService.use(USER_ID, COUPON_ID, Money.of(9_999L)));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
            assertThat(coupon.getStatus()).isEqualTo(CouponStatus.AVAILABLE);
        }

        @DisplayName("이미 사용된 쿠폰이면 CONFLICT 예외가 발생한다")
        @Test
        void throwsConflict_whenAlreadyUsed() {
            // given
            IssuedCoupon coupon = new IssuedCoupon(USER_ID, TEMPLATE_ID);
            coupon.use();
            when(issuedCouponRepository.findById(COUPON_ID)).thenReturn(Optional.of(coupon));
            when(couponTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template()));

            // when
            CoreException ex = assertThrows(CoreException.class,
                () -> couponService.use(USER_ID, COUPON_ID, Money.of(10_000L)));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }
}
