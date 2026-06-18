package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("domain")
class CouponApplicationServiceTest {

    private final CouponRepository couponRepository = mock(CouponRepository.class);
    private final UserCouponRepository userCouponRepository = mock(UserCouponRepository.class);
    private final CouponApplicationService couponApplicationService =
        new CouponApplicationService(couponRepository, userCouponRepository);

    private static final Long USER_ID = 1L;
    private static final Long COUPON_ID = 10L;
    private static final Long USER_COUPON_ID = 100L;
    private static final ZonedDateTime FUTURE = ZonedDateTime.now().plusDays(30);

    private CouponModel stubCoupon() {
        return new CouponModel(COUPON_ID, "신규가입 10% 할인", CouponType.RATE, 10, 0, FUTURE, null, null);
    }

    private UserCouponModel stubUserCoupon() {
        return new UserCouponModel(USER_COUPON_ID, USER_ID, COUPON_ID, CouponStatus.AVAILABLE, 0L, null, null);
    }

    @DisplayName("쿠폰 템플릿 목록 조회 시, ")
    @Nested
    class GetCoupons {

        @DisplayName("쿠폰이 존재하면 CouponAdminInfo Page를 반환한다.")
        @Test
        void returnsCouponAdminInfoPage_whenCouponsExist() {
            // arrange
            PageRequest pageable = PageRequest.of(0, 20);
            Page<CouponModel> couponPage = new PageImpl<>(List.of(stubCoupon()));
            when(couponRepository.findAll(pageable)).thenReturn(couponPage);

            // act
            Page<CouponAdminInfo> result = couponApplicationService.getCoupons(pageable);

            // assert
            assertAll(
                () -> assertThat(result.getTotalElements()).isEqualTo(1),
                () -> assertThat(result.getContent().get(0).id()).isEqualTo(COUPON_ID),
                () -> assertThat(result.getContent().get(0).name()).isEqualTo("신규가입 10% 할인")
            );
        }

        @DisplayName("쿠폰이 없으면 빈 Page를 반환한다.")
        @Test
        void returnsEmptyPage_whenNoCouponsExist() {
            // arrange
            PageRequest pageable = PageRequest.of(0, 20);
            when(couponRepository.findAll(pageable)).thenReturn(Page.empty());

            // act
            Page<CouponAdminInfo> result = couponApplicationService.getCoupons(pageable);

            // assert
            assertThat(result.getTotalElements()).isZero();
        }
    }

    @DisplayName("내 쿠폰 목록 조회 시, ")
    @Nested
    class GetUserCoupons {

        @DisplayName("발급된 쿠폰이 있으면 CouponInfo 목록을 반환한다.")
        @Test
        void returnsCouponInfoList_whenUserHasCoupons() {
            // arrange
            when(userCouponRepository.findAllByUserId(USER_ID)).thenReturn(List.of(stubUserCoupon()));
            when(couponRepository.findById(COUPON_ID)).thenReturn(Optional.of(stubCoupon()));

            // act
            List<CouponInfo> result = couponApplicationService.getUserCoupons(USER_ID);

            // assert
            assertAll(
                () -> assertThat(result).hasSize(1),
                () -> assertThat(result.get(0).couponId()).isEqualTo(COUPON_ID),
                () -> assertThat(result.get(0).name()).isEqualTo("신규가입 10% 할인"),
                () -> assertThat(result.get(0).status()).isEqualTo(CouponStatus.AVAILABLE)
            );
        }

        @DisplayName("발급된 쿠폰이 없으면 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenUserHasNoCoupons() {
            // arrange
            when(userCouponRepository.findAllByUserId(USER_ID)).thenReturn(List.of());

            // act
            List<CouponInfo> result = couponApplicationService.getUserCoupons(USER_ID);

            // assert
            assertThat(result).isEmpty();
        }

        @DisplayName("발급된 쿠폰의 템플릿이 존재하지 않으면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenCouponTemplateDoesNotExist() {
            // arrange
            when(userCouponRepository.findAllByUserId(USER_ID)).thenReturn(List.of(stubUserCoupon()));
            when(couponRepository.findById(COUPON_ID)).thenReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> couponApplicationService.getUserCoupons(USER_ID));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("쿠폰 템플릿 단건 조회 시, ")
    @Nested
    class GetCoupon {

        @DisplayName("쿠폰이 존재하면 CouponAdminInfo를 반환한다.")
        @Test
        void returnsCouponAdminInfo_whenCouponExists() {
            // arrange
            when(couponRepository.findById(COUPON_ID)).thenReturn(Optional.of(stubCoupon()));

            // act
            CouponAdminInfo result = couponApplicationService.getCoupon(COUPON_ID);

            // assert
            assertAll(
                () -> assertThat(result.id()).isEqualTo(COUPON_ID),
                () -> assertThat(result.name()).isEqualTo("신규가입 10% 할인"),
                () -> assertThat(result.type()).isEqualTo(CouponType.RATE)
            );
        }

        @DisplayName("쿠폰이 존재하지 않으면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenCouponDoesNotExist() {
            // arrange
            when(couponRepository.findById(COUPON_ID)).thenReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> couponApplicationService.getCoupon(COUPON_ID));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("쿠폰 템플릿 수정 시, ")
    @Nested
    class UpdateCoupon {

        @DisplayName("쿠폰이 존재하면 update() 후 save()를 호출하고 CouponAdminInfo를 반환한다.")
        @Test
        void savesUpdatedCoupon_whenCouponExists() {
            // arrange
            CouponModel coupon = stubCoupon();
            CouponModel updated = new CouponModel(COUPON_ID, "수정된 쿠폰", CouponType.FIXED, 3000, 10000, FUTURE, null, null);
            when(couponRepository.findById(COUPON_ID)).thenReturn(Optional.of(coupon));
            when(couponRepository.save(coupon)).thenReturn(updated);

            // act
            CouponAdminInfo result = couponApplicationService.updateCoupon(COUPON_ID, "수정된 쿠폰", CouponType.FIXED, 3000, 10000, FUTURE);

            // assert
            verify(couponRepository).save(coupon);
            assertAll(
                () -> assertThat(result.name()).isEqualTo("수정된 쿠폰"),
                () -> assertThat(result.type()).isEqualTo(CouponType.FIXED)
            );
        }

        @DisplayName("쿠폰이 존재하지 않으면 NOT_FOUND 예외가 발생하고 save()가 호출되지 않는다.")
        @Test
        void throwsNotFound_andDoesNotCallSave_whenCouponDoesNotExist() {
            // arrange
            when(couponRepository.findById(COUPON_ID)).thenReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> couponApplicationService.updateCoupon(COUPON_ID, "수정된 쿠폰", CouponType.FIXED, 3000, 10000, FUTURE));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verify(couponRepository, never()).save(any());
        }
    }

    @DisplayName("쿠폰 템플릿 삭제 시, ")
    @Nested
    class DeleteCoupon {

        @DisplayName("쿠폰이 존재하면 delete()를 호출한다.")
        @Test
        void callsDelete_whenCouponExists() {
            // arrange
            when(couponRepository.findById(COUPON_ID)).thenReturn(Optional.of(stubCoupon()));

            // act
            couponApplicationService.deleteCoupon(COUPON_ID);

            // assert
            verify(couponRepository).delete(COUPON_ID);
        }

        @DisplayName("쿠폰이 존재하지 않으면 NOT_FOUND 예외가 발생하고 delete()가 호출되지 않는다.")
        @Test
        void throwsNotFound_andDoesNotCallDelete_whenCouponDoesNotExist() {
            // arrange
            when(couponRepository.findById(COUPON_ID)).thenReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> couponApplicationService.deleteCoupon(COUPON_ID));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verify(couponRepository, never()).delete(any());
        }
    }

    @DisplayName("쿠폰 발급 내역 조회 시, ")
    @Nested
    class GetCouponIssues {

        @DisplayName("쿠폰이 존재하면 발급 내역 Page를 반환한다.")
        @Test
        void returnsIssuePage_whenCouponExists() {
            // arrange
            PageRequest pageable = PageRequest.of(0, 20);
            Page<UserCouponModel> issuePage = new PageImpl<>(List.of(stubUserCoupon()));
            when(couponRepository.findById(COUPON_ID)).thenReturn(Optional.of(stubCoupon()));
            when(userCouponRepository.findAllByCouponId(COUPON_ID, pageable)).thenReturn(issuePage);

            // act
            Page<CouponIssueAdminInfo> result = couponApplicationService.getCouponIssues(COUPON_ID, pageable);

            // assert
            assertAll(
                () -> assertThat(result.getTotalElements()).isEqualTo(1),
                () -> assertThat(result.getContent().get(0).couponId()).isEqualTo(COUPON_ID),
                () -> assertThat(result.getContent().get(0).userId()).isEqualTo(USER_ID)
            );
        }

        @DisplayName("쿠폰이 존재하지 않으면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenCouponDoesNotExist() {
            // arrange
            when(couponRepository.findById(COUPON_ID)).thenReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> couponApplicationService.getCouponIssues(COUPON_ID, PageRequest.of(0, 20)));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("쿠폰 발급 시, ")
    @Nested
    class IssueCoupon {

        @DisplayName("정상 요청이면 UserCouponModel을 저장하고 CouponInfo를 반환한다.")
        @Test
        void returnsCouponInfo_whenValid() {
            // arrange
            UserCouponModel saved = new UserCouponModel(USER_COUPON_ID, USER_ID, COUPON_ID, CouponStatus.AVAILABLE, 0L, null, null);
            when(couponRepository.findById(COUPON_ID)).thenReturn(Optional.of(stubCoupon()));
            when(userCouponRepository.existsByUserIdAndCouponId(USER_ID, COUPON_ID)).thenReturn(false);
            when(userCouponRepository.save(any(UserCouponModel.class))).thenReturn(saved);

            // act
            CouponInfo result = couponApplicationService.issueCoupon(USER_ID, COUPON_ID);

            // assert
            assertThat(result.userCouponId()).isEqualTo(USER_COUPON_ID);
            assertThat(result.couponId()).isEqualTo(COUPON_ID);
            verify(userCouponRepository).save(any(UserCouponModel.class));
        }

        @DisplayName("존재하지 않는 쿠폰이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenCouponDoesNotExist() {
            // arrange
            when(couponRepository.findById(COUPON_ID)).thenReturn(Optional.empty());

            // act & assert
            CoreException result = assertThrows(CoreException.class,
                () -> couponApplicationService.issueCoupon(USER_ID, COUPON_ID));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("만료된 쿠폰이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCouponExpired() {
            // arrange
            CouponModel expiredCoupon = new CouponModel(COUPON_ID, "만료쿠폰", CouponType.FIXED, 1000, 0,
                ZonedDateTime.now().minusDays(1), null, null);
            when(couponRepository.findById(COUPON_ID)).thenReturn(Optional.of(expiredCoupon));

            // act & assert
            CoreException result = assertThrows(CoreException.class,
                () -> couponApplicationService.issueCoupon(USER_ID, COUPON_ID));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이미 발급된 쿠폰이면 CONFLICT 예외가 발생하고 저장이 호출되지 않는다.")
        @Test
        void throwsConflict_whenAlreadyIssued() {
            // arrange
            when(couponRepository.findById(COUPON_ID)).thenReturn(Optional.of(stubCoupon()));
            when(userCouponRepository.existsByUserIdAndCouponId(USER_ID, COUPON_ID)).thenReturn(true);

            // act & assert
            CoreException result = assertThrows(CoreException.class,
                () -> couponApplicationService.issueCoupon(USER_ID, COUPON_ID));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
            verify(userCouponRepository, never()).save(any());
        }
    }
}
