package com.loopers.application.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

@ExtendWith(MockitoExtension.class)
class CouponFacadeTest {

    @Mock
    private CouponRepository couponRepository;

    @InjectMocks
    private CouponFacade couponFacade;

    @DisplayName("쿠폰 템플릿을 생성할 때,")
    @Nested
    class CreateCoupon {

        private final ZonedDateTime expiredAt = ZonedDateTime.now().plusDays(7);

        @DisplayName("유효한 값이면 쿠폰 템플릿을 저장하고 생성 정보를 반환한다.")
        @Test
        void returnsCreateInfo_whenValuesAreValid() {
            // arrange
            given(couponRepository.save(any(CouponModel.class))).willAnswer(invocation -> invocation.getArgument(0));

            // act
            CouponCreateInfo createInfo = couponFacade.createCoupon("신규 쿠폰", DiscountType.FIXED, 5_000, 10_000, expiredAt);

            // assert
            assertAll(
                () -> assertThat(createInfo).isNotNull(),
                () -> then(couponRepository).should().save(any(CouponModel.class))
            );
        }

        @DisplayName("할인 값이 타입 허용 범위를 벗어나면 BAD_REQUEST 예외가 발생하고 저장하지 않는다.")
        @Test
        void throwsBadRequest_whenDiscountValueIsOutOfRange() {
            // arrange & act & assert
            assertAll(
                () -> assertThatThrownBy(() -> couponFacade.createCoupon("정률 쿠폰", DiscountType.RATE, 101, null, expiredAt))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.BAD_REQUEST),
                () -> then(couponRepository).should(never()).save(any(CouponModel.class))
            );
        }
    }
}
