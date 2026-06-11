package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IssuedCouponServiceTest {

    private IssuedCouponService issuedCouponService;
    private IssuedCouponRepository issuedCouponRepository;

    @BeforeEach
    void setUp() {
        issuedCouponRepository = mock(IssuedCouponRepository.class);
        issuedCouponService = new IssuedCouponService(issuedCouponRepository);
    }

    @DisplayName("특정 쿠폰 템플릿의 발급 내역을 페이지 단위로 조회할 때,")
    @Nested
    class FindAllByCouponTemplateId {

        @DisplayName("발급 내역이 존재하면 해당 목록이 페이지 단위로 반환된다.")
        @Test
        void issuedCouponsAreListedByPage_whenIssuesExist() {
            // given
            Long couponTemplateId = 1L;
            Pageable pageable = PageRequest.of(0, 20);
            IssuedCouponModel issued = new IssuedCouponModel(couponTemplateId, 10L);
            when(issuedCouponRepository.findAllByCouponTemplateId(couponTemplateId, pageable))
                    .thenReturn(new PageImpl<>(List.of(issued)));

            // when
            Page<IssuedCouponModel> result = issuedCouponService.findAllByCouponTemplateId(couponTemplateId, pageable);

            // then
            assertThat(result.getContent()).hasSize(1);
        }

        @DisplayName("발급 내역이 없으면 빈 페이지가 반환된다.")
        @Test
        void returnsEmptyPage_whenNoIssuesExist() {
            // given
            Long couponTemplateId = 1L;
            Pageable pageable = PageRequest.of(0, 20);
            when(issuedCouponRepository.findAllByCouponTemplateId(couponTemplateId, pageable))
                    .thenReturn(new PageImpl<>(List.of()));

            // when
            Page<IssuedCouponModel> result = issuedCouponService.findAllByCouponTemplateId(couponTemplateId, pageable);

            // then
            assertThat(result.getContent()).isEmpty();
        }
    }

    @DisplayName("내 발급 쿠폰 목록을 조회할 때,")
    @Nested
    class GetMyIssuedCoupons {

        @DisplayName("발급받은 쿠폰이 있으면 해당 목록이 반환된다.")
        @Test
        void myIssuedCouponsAreListed_whenCouponsExist() {
            // given
            Long userId = 10L;
            IssuedCouponModel issued1 = new IssuedCouponModel(1L, userId);
            IssuedCouponModel issued2 = new IssuedCouponModel(2L, userId);
            when(issuedCouponRepository.findAllByUserId(userId))
                    .thenReturn(List.of(issued1, issued2));

            // when
            List<IssuedCouponModel> result = issuedCouponService.getMyIssuedCoupons(userId);

            // then
            assertThat(result).hasSize(2);
        }

        @DisplayName("발급받은 쿠폰이 없으면 빈 목록이 반환된다.")
        @Test
        void returnsEmptyList_whenNoCouponsExist() {
            // given
            Long userId = 10L;
            when(issuedCouponRepository.findAllByUserId(userId))
                    .thenReturn(List.of());

            // when
            List<IssuedCouponModel> result = issuedCouponService.getMyIssuedCoupons(userId);

            // then
            assertThat(result).isEmpty();
        }
    }

    @DisplayName("쿠폰을 발급할 때,")
    @Nested
    class Issue {

        @DisplayName("유효한 템플릿 ID와 유저 ID로 발급하면 AVAILABLE 상태의 발급 쿠폰이 반환된다.")
        @Test
        void issuedCouponIsReturned_withAvailableStatus() {
            // given
            Long couponTemplateId = 1L;
            Long userId = 10L;
            when(issuedCouponRepository.existsByCouponTemplateIdAndUserId(couponTemplateId, userId)).thenReturn(false);
            when(issuedCouponRepository.save(any(IssuedCouponModel.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            IssuedCouponModel result = issuedCouponService.issue(couponTemplateId, userId);

            // then
            assertAll(
                    () -> assertThat(result.getCouponTemplateId()).isEqualTo(couponTemplateId),
                    () -> assertThat(result.getUserId()).isEqualTo(userId),
                    () -> assertThat(result.getStatus()).isEqualTo(CouponStatus.AVAILABLE)
            );
        }

        @DisplayName("이미 발급받은 쿠폰이면 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenCouponAlreadyIssued() {
            // given
            Long couponTemplateId = 1L;
            Long userId = 10L;
            when(issuedCouponRepository.existsByCouponTemplateIdAndUserId(couponTemplateId, userId)).thenReturn(true);

            // when
            CoreException exception = assertThrows(CoreException.class,
                    () -> issuedCouponService.issue(couponTemplateId, userId));

            // then
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("발급 쿠폰을 ID와 유저 ID로 조회할 때,")
    @Nested
    class GetByIdForUser {

        @DisplayName("존재하는 발급 쿠폰이고 소유자이면 IssuedCouponModel이 반환된다.")
        @Test
        void returnsIssuedCouponModel_whenExistsAndOwner() {
            // given
            Long issuedCouponId = 1L;
            Long userId = 100L;
            IssuedCouponModel issued = new IssuedCouponModel(1L, userId);
            when(issuedCouponRepository.findById(issuedCouponId)).thenReturn(Optional.of(issued));

            // when
            IssuedCouponModel result = issuedCouponService.getMyIssuedCoupon(issuedCouponId, userId);

            // then
            assertThat(result.getUserId()).isEqualTo(userId);
        }

        @DisplayName("존재하지 않는 발급 쿠폰 ID이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenIssuedCouponDoesNotExist() {
            // given
            when(issuedCouponRepository.findById(999L)).thenReturn(Optional.empty());

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> issuedCouponService.getMyIssuedCoupon(999L, 100L));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("쿠폰 소유자가 아니면 FORBIDDEN 예외가 발생한다.")
        @Test
        void throwsForbiddenException_whenUserIsNotOwner() {
            // given
            Long issuedCouponId = 1L;
            IssuedCouponModel issued = new IssuedCouponModel(1L, 100L);
            when(issuedCouponRepository.findById(issuedCouponId)).thenReturn(Optional.of(issued));

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> issuedCouponService.getMyIssuedCoupon(issuedCouponId, 999L));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
        }
    }

    @DisplayName("발급 쿠폰을 사용할 때,")
    @Nested
    class Use {

        @DisplayName("AVAILABLE 상태이면 사용 처리되어 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenStatusIsAvailable() {
            // given
            Long issuedCouponId = 1L;
            IssuedCouponModel issued = new IssuedCouponModel(1L, 100L);
            when(issuedCouponRepository.findById(issuedCouponId)).thenReturn(Optional.of(issued));
            when(issuedCouponRepository.save(issued)).thenReturn(issued);

            // when & then
            assertDoesNotThrow(() -> issuedCouponService.use(issuedCouponId));
        }

        @DisplayName("존재하지 않는 발급 쿠폰 ID이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenIssuedCouponDoesNotExist() {
            // given
            when(issuedCouponRepository.findById(999L)).thenReturn(Optional.empty());

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> issuedCouponService.use(999L));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("이미 사용된 쿠폰이면 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflictException_whenCouponAlreadyUsed() {
            // given
            Long issuedCouponId = 1L;
            IssuedCouponModel issued = new IssuedCouponModel(1L, 100L);
            ReflectionTestUtils.setField(issued, "status", CouponStatus.USED);
            when(issuedCouponRepository.findById(issuedCouponId)).thenReturn(Optional.of(issued));

            // when
            CoreException result = assertThrows(CoreException.class,
                    () -> issuedCouponService.use(issuedCouponId));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }
}
