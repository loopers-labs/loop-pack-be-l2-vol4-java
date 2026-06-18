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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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

        @DisplayName("유효한 정책을 발급하면, 해당 사용자 소유의 AVAILABLE 사용자 쿠폰을 저장한다.")
        @Test
        void issuesAvailableUserCoupon_whenPolicyIsValid() {
            // given
            Long userId = 1L;
            Long policyId = 10L;
            given(couponPolicyRepository.findActiveById(policyId)).willReturn(Optional.of(policy()));
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
            given(couponPolicyRepository.findActiveById(policyId)).willReturn(Optional.empty());

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

        @DisplayName("같은 정책을 발급할 때, 기존 사용자 쿠폰을 조회하지 않고 새 사용자 쿠폰을 저장한다. (중복 발급 허용)")
        @Test
        void doesNotCheckExistingCoupons_whenIssuing() {
            // given
            Long userId = 1L;
            Long policyId = 10L;
            given(couponPolicyRepository.findActiveById(policyId)).willReturn(Optional.of(policy()));
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

        @DisplayName("해당 사용자의 사용자 쿠폰 목록을 그대로 반환한다.")
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

        @DisplayName("존재하지 않는 사용자 쿠폰을 사용하면, COUPON_NOT_FOUND 예외가 발생하고 정책을 조회하지 않는다.")
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

        @DisplayName("이미 사용된 쿠폰을 다시 사용하면, 사용자 쿠폰의 COUPON_ALREADY_USED 예외가 그대로 전파된다.")
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

    @DisplayName("주문에 쿠폰을 적용할 때, ")
    @Nested
    class Apply {

        @DisplayName("couponId 가 null 이면, 저장소를 조회하지 않고 할인 없음(0원) 결과를 반환한다.")
        @Test
        void returnsNoDiscount_whenCouponIdIsNull() {
            // given
            Long userId = 1L;
            Long userCouponId = null;

            // when
            DiscountResult result = couponService.apply(userId, userCouponId, 10_000L);

            // then
            assertAll(
                () -> assertThat(result.usedCouponId()).isNull(),
                () -> assertThat(result.amount()).isEqualTo(0L),
                () -> verify(userCouponRepository, never()).findById(any())
            );
        }

        @DisplayName("유효한 쿠폰을 적용하면, 사용한 쿠폰 식별자와 할인액을 담은 결과를 반환하고 USED 상태로 전이한다.")
        @Test
        void returnsDiscountResultAndUsesCoupon_whenCouponIsValid() {
            // given
            Long userId = 1L;
            Long userCouponId = 100L;
            UserCoupon userCoupon = UserCoupon.issue(userId, policy(), NOW);
            given(userCouponRepository.findById(userCouponId)).willReturn(Optional.of(userCoupon));

            // when
            DiscountResult result = couponService.apply(userId, userCouponId, 10_000L);

            // then
            assertAll(
                () -> assertThat(result.usedCouponId()).isEqualTo(100L),
                () -> assertThat(result.amount()).isEqualTo(3_000L),
                () -> assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.USED)
            );
        }

        @DisplayName("타 유저 소유 쿠폰을 적용하면, COUPON_NOT_OWNED 예외가 전파된다.")
        @Test
        void propagatesCouponNotOwnedException_whenCouponBelongsToAnotherUser() {
            // given
            Long ownerId = 1L;
            Long requesterId = 2L;
            Long userCouponId = 100L;
            UserCoupon userCoupon = UserCoupon.issue(ownerId, policy(), NOW);
            given(userCouponRepository.findById(userCouponId)).willReturn(Optional.of(userCoupon));

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> couponService.apply(requesterId, userCouponId, 10_000L));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.COUPON_NOT_OWNED),
                () -> assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.AVAILABLE)
            );
        }
    }

    @DisplayName("어드민이 쿠폰 정책을 생성할 때, ")
    @Nested
    class CreatePolicy {

        @DisplayName("유효한 값으로 생성하면, 정책을 저장하고 저장된 정책을 반환한다.")
        @Test
        void createsAndReturnsPolicy() {
            // given
            given(couponPolicyRepository.save(any(CouponPolicy.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            CouponPolicy created = couponService.createPolicy("신규 쿠폰", CouponType.FIXED, 3_000L, 10_000L, EXPIRED_AT);

            // then
            assertAll(
                () -> assertThat(created.getName()).isEqualTo("신규 쿠폰"),
                () -> assertThat(created.getType()).isEqualTo(CouponType.FIXED),
                () -> assertThat(created.getValue()).isEqualTo(3_000L),
                () -> assertThat(created.getMinOrderAmount()).isEqualTo(10_000L),
                () -> verify(couponPolicyRepository).save(any(CouponPolicy.class))
            );
        }
    }

    @DisplayName("어드민이 쿠폰 정책을 단건 조회할 때, ")
    @Nested
    class GetPolicy {

        @DisplayName("존재하는 정책이면, 해당 정책을 반환한다. (삭제 포함)")
        @Test
        void returnsPolicy_whenExists() {
            // given
            Long policyId = 10L;
            CouponPolicy policy = policy();
            given(couponPolicyRepository.findById(policyId)).willReturn(Optional.of(policy));

            // when
            CouponPolicy found = couponService.getPolicy(policyId);

            // then
            assertThat(found).isSameAs(policy);
        }

        @DisplayName("존재하지 않는 정책이면, COUPON_POLICY_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsCouponPolicyNotFound_whenNotExists() {
            // given
            Long policyId = 999L;
            given(couponPolicyRepository.findById(policyId)).willReturn(Optional.empty());

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> couponService.getPolicy(policyId));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.COUPON_POLICY_NOT_FOUND),
                () -> assertThat(result.getCustomMessage()).isEqualTo("쿠폰 정책을 찾을 수 없습니다.")
            );
        }
    }

    @DisplayName("어드민이 쿠폰 정책 목록을 조회할 때, ")
    @Nested
    class GetPolicies {

        @DisplayName("삭제 포함 전체 정책 페이지를 그대로 반환한다.")
        @Test
        void returnsAllPoliciesPage() {
            // given
            Pageable pageable = PageRequest.of(0, 20);
            given(couponPolicyRepository.findAll(pageable)).willReturn(new PageImpl<>(List.of(policy(), policy())));

            // when
            Page<CouponPolicy> found = couponService.getPolicies(pageable);

            // then
            assertThat(found.getContent()).hasSize(2);
        }
    }

    @DisplayName("어드민이 쿠폰 정책을 수정할 때, ")
    @Nested
    class UpdatePolicy {

        @DisplayName("active 정책을 수정하면, 메타 정보가 갱신된 정책을 저장하고 반환한다.")
        @Test
        void updatesActivePolicy() {
            // given
            Long policyId = 10L;
            CouponPolicy policy = policy();
            given(couponPolicyRepository.findActiveById(policyId)).willReturn(Optional.of(policy));
            given(couponPolicyRepository.save(any(CouponPolicy.class))).willAnswer(invocation -> invocation.getArgument(0));
            ZonedDateTime newExpiredAt = ZonedDateTime.parse("2100-01-31T23:59:59+09:00");

            // when
            CouponPolicy updated = couponService.updatePolicy(policyId, "변경된 쿠폰", 20_000L, newExpiredAt);

            // then
            assertAll(
                () -> assertThat(updated.getName()).isEqualTo("변경된 쿠폰"),
                () -> assertThat(updated.getMinOrderAmount()).isEqualTo(20_000L),
                () -> assertThat(updated.getExpiredAt()).isEqualTo(newExpiredAt),
                () -> verify(couponPolicyRepository).save(policy)
            );
        }

        @DisplayName("active 정책이 없으면(미존재 또는 삭제됨), COUPON_POLICY_NOT_FOUND 예외가 발생하고 저장하지 않는다.")
        @Test
        void throwsCouponPolicyNotFound_whenActivePolicyDoesNotExist() {
            // given
            Long policyId = 999L;
            given(couponPolicyRepository.findActiveById(policyId)).willReturn(Optional.empty());

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> couponService.updatePolicy(policyId, "변경", 10_000L, EXPIRED_AT));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.COUPON_POLICY_NOT_FOUND),
                () -> verify(couponPolicyRepository, never()).save(any(CouponPolicy.class))
            );
        }
    }

    @DisplayName("어드민이 쿠폰 정책을 삭제할 때, ")
    @Nested
    class DeletePolicy {

        @DisplayName("발급된 사용자 쿠폰 존재 여부와 무관하게, 정책을 soft-delete 한다.")
        @Test
        void softDeletesPolicy_regardlessOfIssuedCoupons() {
            // given
            Long policyId = 10L;
            CouponPolicy policy = policy();
            given(couponPolicyRepository.findById(policyId)).willReturn(Optional.of(policy));

            // when
            couponService.deletePolicy(policyId);

            // then
            assertAll(
                () -> assertThat(policy.getDeletedAt()).isNotNull(),
                () -> verify(userCouponRepository, never()).findByCouponPolicyId(any(), any())
            );
        }

        @DisplayName("존재하지 않는 정책을 삭제하면, COUPON_POLICY_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsCouponPolicyNotFound_whenPolicyDoesNotExist() {
            // given
            Long policyId = 999L;
            given(couponPolicyRepository.findById(policyId)).willReturn(Optional.empty());

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> couponService.deletePolicy(policyId));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.COUPON_POLICY_NOT_FOUND);
        }
    }

    @DisplayName("어드민이 정책별 발급 내역을 조회할 때, ")
    @Nested
    class GetIssuedCoupons {

        @DisplayName("존재하는 정책이면, 해당 정책으로 발급된 사용자 쿠폰 페이지를 반환한다.")
        @Test
        void returnsIssuedCouponsPage_whenPolicyExists() {
            // given
            Long policyId = 10L;
            Pageable pageable = PageRequest.of(0, 20);
            CouponPolicy policy = policy();
            given(couponPolicyRepository.findById(policyId)).willReturn(Optional.of(policy));
            UserCoupon uc1 = UserCoupon.issue(1L, policy, NOW);
            UserCoupon uc2 = UserCoupon.issue(2L, policy, NOW);
            given(userCouponRepository.findByCouponPolicyId(policyId, pageable)).willReturn(new PageImpl<>(List.of(uc1, uc2)));

            // when
            Page<UserCoupon> found = couponService.getIssuedCoupons(policyId, pageable);

            // then
            assertThat(found.getContent()).containsExactly(uc1, uc2);
        }

        @DisplayName("존재하지 않는 정책이면, COUPON_POLICY_NOT_FOUND 예외가 발생하고 발급 내역을 조회하지 않는다.")
        @Test
        void throwsCouponPolicyNotFound_whenPolicyDoesNotExist() {
            // given
            Long policyId = 999L;
            Pageable pageable = PageRequest.of(0, 20);
            given(couponPolicyRepository.findById(policyId)).willReturn(Optional.empty());

            // when
            CoreException result = assertThrows(CoreException.class,
                () -> couponService.getIssuedCoupons(policyId, pageable));

            // then
            assertAll(
                () -> assertThat(result.getErrorType()).isEqualTo(ErrorType.COUPON_POLICY_NOT_FOUND),
                () -> verify(userCouponRepository, never()).findByCouponPolicyId(any(), any())
            );
        }
    }
}
