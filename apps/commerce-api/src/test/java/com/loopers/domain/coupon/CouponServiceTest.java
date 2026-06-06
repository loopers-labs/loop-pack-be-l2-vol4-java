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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    private static final ZonedDateTime EXPIRED_AT = ZonedDateTime.parse("2099-12-31T23:59:59+09:00");
    private static final ZonedDateTime NOW = ZonedDateTime.parse("2026-06-06T00:00:00+09:00");

    @Mock
    private CouponPolicyRepository couponPolicyRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @InjectMocks
    private CouponService couponService;

    private CouponPolicy policy() {
        return new CouponPolicy("3천원 할인", CouponType.FIXED, 3_000L, 10_000L, EXPIRED_AT);
    }

    @DisplayName("쿠폰을 발급할 때, ")
    @Nested
    class Issue {

        @DisplayName("유효한 정책을 발급하면, 해당 사용자 소유의 AVAILABLE 발급분을 저장한다.")
        @Test
        void issuesAvailableUserCoupon_whenPolicyIsValid() {
            // given
            Long userId = 1L;
            Long policyId = 10L;
            given(couponPolicyRepository.findById(policyId)).willReturn(Optional.of(policy()));
            given(userCouponRepository.save(any(UserCoupon.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            UserCoupon issued = couponService.issue(userId, policyId);

            // then
            assertAll(
                () -> assertThat(issued.getUserId()).isEqualTo(userId),
                () -> assertThat(issued.getStatus()).isEqualTo(UserCouponStatus.AVAILABLE),
                () -> assertThat(issued.getUsedAt()).isNull(),
                () -> verify(userCouponRepository).save(any(UserCoupon.class))
            );
        }

        @DisplayName("존재하지 않는 정책으로 발급하면, COUPON_POLICY_NOT_FOUND 예외가 발생하고 저장하지 않는다.")
        @Test
        void throwsCouponPolicyNotFoundException_whenPolicyDoesNotExist() {
            // given
            Long userId = 1L;
            Long policyId = 999L;
            given(couponPolicyRepository.findById(policyId)).willReturn(Optional.empty());

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> couponService.issue(userId, policyId));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.COUPON_POLICY_NOT_FOUND),
                () -> assertThat(result.getCustomMessage()).isEqualTo("쿠폰 정책을 찾을 수 없습니다."),
                () -> verify(userCouponRepository, never()).save(any(UserCoupon.class))
            );
        }

        @DisplayName("같은 정책을 발급할 때, 기존 발급분을 조회하지 않고 새 발급분을 저장한다. (중복 발급 허용)")
        @Test
        void doesNotCheckExistingCoupons_whenIssuing() {
            // given
            Long userId = 1L;
            Long policyId = 10L;
            given(couponPolicyRepository.findById(policyId)).willReturn(Optional.of(policy()));
            given(userCouponRepository.save(any(UserCoupon.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            couponService.issue(userId, policyId);

            // then
            assertAll(
                () -> verify(userCouponRepository, never()).findByUserId(any()),
                () -> verify(userCouponRepository).save(any(UserCoupon.class))
            );
        }
    }

    @DisplayName("내 쿠폰을 조회할 때, ")
    @Nested
    class GetMyCoupons {

        @DisplayName("해당 사용자의 발급분 목록을 그대로 반환한다.")
        @Test
        void returnsUserCouponsOfUser() {
            // given
            Long userId = 1L;
            UserCoupon mine1 = UserCoupon.issue(userId, policy(), NOW);
            UserCoupon mine2 = UserCoupon.issue(userId, policy(), NOW);
            given(userCouponRepository.findByUserId(userId)).willReturn(List.of(mine1, mine2));

            // when
            List<UserCoupon> found = couponService.getMyCoupons(userId);

            // then
            assertThat(found).containsExactly(mine1, mine2);
        }
    }

    @DisplayName("쿠폰을 단건 사용할 때, ")
    @Nested
    class Use {

        @DisplayName("소유자가 유효한 쿠폰을 사용하면, 할인액을 반환하고 USED 상태로 전이한다.")
        @Test
        void usesCouponAndReturnsDiscount_whenAllInvariantsAreSatisfied() {
            // given
            Long userId = 1L;
            Long userCouponId = 100L;
            UserCoupon userCoupon = UserCoupon.issue(userId, policy(), NOW);
            given(userCouponRepository.findById(userCouponId)).willReturn(Optional.of(userCoupon));

            // when
            long discount = couponService.use(userId, userCouponId, 10_000L);

            // then
            assertAll(
                () -> assertThat(discount).isEqualTo(3_000L),
                () -> assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.USED),
                () -> assertThat(userCoupon.getUsedAt()).isNotNull()
            );
        }

        @DisplayName("존재하지 않는 발급분을 사용하면, COUPON_NOT_FOUND 예외가 발생하고 정책을 조회하지 않는다.")
        @Test
        void throwsCouponNotFoundException_whenUserCouponDoesNotExist() {
            // given
            Long userId = 1L;
            Long userCouponId = 999L;
            given(userCouponRepository.findById(userCouponId)).willReturn(Optional.empty());

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> couponService.use(userId, userCouponId, 10_000L));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.COUPON_NOT_FOUND),
                () -> assertThat(result.getCustomMessage()).isEqualTo("쿠폰을 찾을 수 없습니다."),
                () -> verify(couponPolicyRepository, never()).findById(any())
            );
        }

        @DisplayName("이미 사용된 쿠폰을 다시 사용하면, 발급분의 COUPON_ALREADY_USED 예외가 그대로 전파된다.")
        @Test
        void propagatesCouponAlreadyUsedException_whenCouponIsAlreadyUsed() {
            // given
            Long userId = 1L;
            Long userCouponId = 100L;
            UserCoupon userCoupon = UserCoupon.issue(userId, policy(), NOW);
            userCoupon.use(userId, 10_000L, NOW);
            given(userCouponRepository.findById(userCouponId)).willReturn(Optional.of(userCoupon));

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> couponService.use(userId, userCouponId, 10_000L));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.COUPON_ALREADY_USED),
                () -> assertThat(result.getCustomMessage()).isEqualTo("이미 사용된 쿠폰입니다.")
            );
        }
    }
}
