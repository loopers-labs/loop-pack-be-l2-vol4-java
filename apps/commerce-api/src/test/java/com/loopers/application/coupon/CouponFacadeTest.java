package com.loopers.application.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

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

        private final ZonedDateTime now = ZonedDateTime.now();
        private final ZonedDateTime expiredAt = now.plusDays(7);

        @DisplayName("유효한 값이면 쿠폰 템플릿을 저장하고 생성 정보를 반환한다.")
        @Test
        void returnsCreateInfo_whenValuesAreValid() {
            // arrange
            given(couponRepository.save(any(CouponModel.class))).willAnswer(invocation -> invocation.getArgument(0));

            // act
            CouponCreateInfo createInfo = couponFacade.createCoupon("신규 쿠폰", DiscountType.FIXED, 5_000, 10_000, expiredAt, now);

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
                () -> assertThatThrownBy(() -> couponFacade.createCoupon("정률 쿠폰", DiscountType.RATE, 101, null, expiredAt, now))
                    .isInstanceOf(CoreException.class)
                    .extracting("errorType")
                    .isEqualTo(ErrorType.BAD_REQUEST),
                () -> then(couponRepository).should(never()).save(any(CouponModel.class))
            );
        }
    }

    @DisplayName("쿠폰 템플릿을 수정할 때,")
    @Nested
    class UpdateCoupon {

        private final Long couponId = 1L;
        private final ZonedDateTime now = ZonedDateTime.now();
        private final ZonedDateTime expiredAt = now.plusDays(7);

        @DisplayName("대상이 활성 존재하면 속성을 갱신하고 정보를 반환한다.")
        @Test
        void updatesCoupon_whenTargetIsActive() {
            // arrange
            CouponModel coupon = CouponModel.builder()
                .rawName("기존 쿠폰")
                .type(DiscountType.FIXED)
                .rawValue(5_000)
                .rawMinOrderAmount(10_000)
                .rawExpiredAt(expiredAt)
                .now(now)
                .build();
            given(couponRepository.getActiveById(couponId)).willReturn(coupon);

            // act
            CouponUpdateInfo updateInfo = couponFacade.updateCoupon(couponId, "변경 쿠폰", DiscountType.RATE, 20, 50_000, expiredAt, now);

            // assert
            assertAll(
                () -> assertThat(updateInfo).isNotNull(),
                () -> assertThat(coupon.getName().value()).isEqualTo("변경 쿠폰"),
                () -> assertThat(coupon.getType()).isEqualTo(DiscountType.RATE),
                () -> assertThat(coupon.getDiscountValue()).isEqualTo(20)
            );
        }

        @DisplayName("대상 템플릿이 활성 상태로 존재하지 않으면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenTargetIsAbsent() {
            // arrange
            given(couponRepository.getActiveById(couponId)).willThrow(new CoreException(ErrorType.NOT_FOUND));

            // act & assert
            assertThatThrownBy(() -> couponFacade.updateCoupon(couponId, "변경 쿠폰", DiscountType.FIXED, 3_000, null, expiredAt, now))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("쿠폰 템플릿을 삭제할 때,")
    @Nested
    class DeleteCoupon {

        private final Long couponId = 1L;

        @DisplayName("대상이 활성 존재하면 템플릿을 삭제한다.")
        @Test
        void deletesCoupon_whenTargetIsActive() {
            // arrange
            CouponModel coupon = CouponModel.builder()
                .rawName("기존 쿠폰")
                .type(DiscountType.FIXED)
                .rawValue(5_000)
                .rawMinOrderAmount(10_000)
                .rawExpiredAt(ZonedDateTime.now().plusDays(7))
                .now(ZonedDateTime.now())
                .build();
            given(couponRepository.findActiveById(couponId)).willReturn(Optional.of(coupon));

            // act
            couponFacade.deleteCoupon(couponId);

            // assert
            assertThat(coupon.getDeletedAt()).isNotNull();
        }

        @DisplayName("대상이 없거나 이미 삭제되었으면 별도 동작 없이 마무리한다(멱등).")
        @Test
        void doesNothing_whenTargetIsAbsent() {
            // arrange
            given(couponRepository.findActiveById(couponId)).willReturn(Optional.empty());

            // act & assert
            couponFacade.deleteCoupon(couponId);
        }
    }

    @DisplayName("쿠폰 템플릿 목록을 조회할 때,")
    @Nested
    class ReadCoupons {

        @DisplayName("활성 템플릿 페이지를 CouponAdminInfo로 매핑해 반환한다.")
        @Test
        void returnsMappedPage() {
            // arrange
            CouponModel coupon = CouponModel.builder()
                .rawName("신규 쿠폰")
                .type(DiscountType.FIXED)
                .rawValue(5_000)
                .rawMinOrderAmount(10_000)
                .rawExpiredAt(ZonedDateTime.now().plusDays(7))
                .now(ZonedDateTime.now())
                .build();
            given(couponRepository.findActiveByPage(0, 20)).willReturn(new PageImpl<>(List.of(coupon)));

            // act
            Page<CouponAdminInfo> couponsInfo = couponFacade.readCoupons(0, 20);

            // assert
            assertAll(
                () -> assertThat(couponsInfo.getTotalElements()).isEqualTo(1),
                () -> assertThat(couponsInfo.getContent()).extracting(CouponAdminInfo::name).containsExactly("신규 쿠폰"),
                () -> then(couponRepository).should().findActiveByPage(0, 20)
            );
        }
    }

    @DisplayName("쿠폰 템플릿을 단건 조회할 때,")
    @Nested
    class ReadCoupon {

        private final Long couponId = 1L;

        @DisplayName("활성 템플릿이 존재하면 정보를 반환한다.")
        @Test
        void returnsCouponAdminInfo_whenActiveExists() {
            // arrange
            CouponModel coupon = CouponModel.builder()
                .rawName("신규 쿠폰")
                .type(DiscountType.FIXED)
                .rawValue(5_000)
                .rawMinOrderAmount(10_000)
                .rawExpiredAt(ZonedDateTime.now().plusDays(7))
                .now(ZonedDateTime.now())
                .build();
            given(couponRepository.getActiveById(couponId)).willReturn(coupon);

            // act
            CouponAdminInfo couponAdminInfo = couponFacade.readCoupon(couponId);

            // assert
            assertAll(
                () -> assertThat(couponAdminInfo.name()).isEqualTo("신규 쿠폰"),
                () -> assertThat(couponAdminInfo.discountType()).isEqualTo(DiscountType.FIXED),
                () -> assertThat(couponAdminInfo.discountValue()).isEqualTo(5_000),
                () -> assertThat(couponAdminInfo.minOrderAmount()).isEqualTo(10_000)
            );
        }

        @DisplayName("활성 템플릿이 존재하지 않으면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenActiveAbsent() {
            // arrange
            given(couponRepository.getActiveById(couponId)).willThrow(new CoreException(ErrorType.NOT_FOUND));

            // act & assert
            assertThatThrownBy(() -> couponFacade.readCoupon(couponId))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
