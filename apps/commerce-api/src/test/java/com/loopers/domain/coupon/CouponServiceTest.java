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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock
    private CouponTemplateRepository couponTemplateRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @InjectMocks
    private CouponService couponService;

    private static final ZonedDateTime FUTURE = ZonedDateTime.now().plusDays(30);
    private static final ZonedDateTime PAST = ZonedDateTime.now().minusDays(1);

    private CouponTemplate fixedTemplate() {
        return new CouponTemplate("1000원 할인", CouponType.FIXED, 1000L, null, FUTURE);
    }

    @DisplayName("쿠폰을 발급할 때,")
    @Nested
    class IssueCoupon {

        @DisplayName("유효한 템플릿으로 발급하면 AVAILABLE 상태의 UserCoupon이 저장된다.")
        @Test
        void issues_coupon_successfully() {
            CouponTemplate template = fixedTemplate();
            when(couponTemplateRepository.find(1L)).thenReturn(Optional.of(template));
            when(userCouponRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UserCoupon result = couponService.issueCoupon(1L, 1L);

            assertThat(result.getStatus()).isEqualTo(CouponStatus.AVAILABLE);
            assertThat(result.getMemberId()).isEqualTo(1L);
            verify(userCouponRepository).save(any(UserCoupon.class));
        }

        @DisplayName("존재하지 않는 템플릿 ID면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throws_not_found_when_template_not_exist() {
            when(couponTemplateRepository.find(99L)).thenReturn(Optional.empty());

            CoreException ex = assertThrows(CoreException.class,
                    () -> couponService.issueCoupon(1L, 99L));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("만료된 템플릿으로 발급 요청하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_template_is_expired() {
            CouponTemplate expired = new CouponTemplate("만료 쿠폰", CouponType.FIXED, 1000L, null, PAST);
            when(couponTemplateRepository.find(1L)).thenReturn(Optional.of(expired));

            CoreException ex = assertThrows(CoreException.class,
                    () -> couponService.issueCoupon(1L, 1L));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("쿠폰을 사용할 때,")
    @Nested
    class ValidateAndUseCoupon {

        @DisplayName("유효한 쿠폰이면 할인 금액을 반환하고 USED 상태로 변경된다.")
        @Test
        void uses_coupon_and_returns_discount() {
            CouponTemplate template = fixedTemplate();
            UserCoupon userCoupon = new UserCoupon(1L, template);
            when(userCouponRepository.findWithLock(1L)).thenReturn(Optional.of(userCoupon));
            when(couponTemplateRepository.find(any())).thenReturn(Optional.of(template));

            long discount = couponService.validateAndUseCoupon(1L, 1L, 10000L);

            assertThat(discount).isEqualTo(1000L);
            assertThat(userCoupon.getStatus()).isEqualTo(CouponStatus.USED);
        }

        @DisplayName("존재하지 않는 쿠폰이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throws_not_found_when_coupon_not_exist() {
            when(userCouponRepository.findWithLock(99L)).thenReturn(Optional.empty());

            CoreException ex = assertThrows(CoreException.class,
                    () -> couponService.validateAndUseCoupon(1L, 99L, 10000L));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("타인의 쿠폰을 사용하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_not_owner() {
            CouponTemplate template = fixedTemplate();
            UserCoupon userCoupon = new UserCoupon(2L, template);
            when(userCouponRepository.findWithLock(1L)).thenReturn(Optional.of(userCoupon));

            CoreException ex = assertThrows(CoreException.class,
                    () -> couponService.validateAndUseCoupon(1L, 1L, 10000L));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이미 사용된 쿠폰이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throws_when_already_used() {
            CouponTemplate template = fixedTemplate();
            UserCoupon userCoupon = new UserCoupon(1L, template);
            userCoupon.use();
            when(userCouponRepository.findWithLock(1L)).thenReturn(Optional.of(userCoupon));
            when(couponTemplateRepository.find(any())).thenReturn(Optional.of(template));

            CoreException ex = assertThrows(CoreException.class,
                    () -> couponService.validateAndUseCoupon(1L, 1L, 10000L));

            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
