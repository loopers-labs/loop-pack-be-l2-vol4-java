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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponAdminServiceTest {

    private static final Long TEMPLATE_ID = 100L;

    @Mock
    private CouponTemplateRepository couponTemplateRepository;

    @Mock
    private IssuedCouponRepository issuedCouponRepository;

    @InjectMocks
    private CouponAdminService couponAdminService;

    private CouponTemplate template() {
        return new CouponTemplate("쿠폰", CouponType.FIXED, 3_000L, null, ZonedDateTime.now().plusDays(7));
    }

    @DisplayName("쿠폰 템플릿 삭제 시")
    @Nested
    class Delete {

        @DisplayName("발급된 쿠폰이 없으면 템플릿이 삭제된다")
        @Test
        void deletesTemplate_whenNoIssuedCoupon() {
            // given
            CouponTemplate template = template();
            when(couponTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template));
            when(issuedCouponRepository.existsByCouponTemplateId(template.getId())).thenReturn(false);

            // when
            couponAdminService.delete(TEMPLATE_ID);

            // then
            verify(couponTemplateRepository).deleteById(template.getId());
        }

        @DisplayName("발급된 쿠폰이 있으면 CONFLICT 예외가 발생하고 삭제하지 않는다")
        @Test
        void throwsConflict_whenIssuedCouponExists() {
            // given
            CouponTemplate template = template();
            when(couponTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template));
            when(issuedCouponRepository.existsByCouponTemplateId(template.getId())).thenReturn(true);

            // when
            CoreException ex = assertThrows(CoreException.class, () -> couponAdminService.delete(TEMPLATE_ID));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
            verify(couponTemplateRepository, never()).deleteById(template.getId());
        }

        @DisplayName("템플릿이 존재하지 않으면 NOT_FOUND 예외가 발생한다")
        @Test
        void throwsNotFound_whenTemplateMissing() {
            // given
            when(couponTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.empty());

            // when
            CoreException ex = assertThrows(CoreException.class, () -> couponAdminService.delete(TEMPLATE_ID));

            // then
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
