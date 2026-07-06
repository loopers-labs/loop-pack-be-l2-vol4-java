package com.loopers.domain.coupon;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserCouponModelTest {

    private static final ZonedDateTime NOW = ZonedDateTime.now();
    private static final ZonedDateTime FUTURE = NOW.plusDays(30);
    private static final ZonedDateTime PAST = NOW.minusDays(1);

    /** 정액 1,000원 / 최소금액 없음 발급분. */
    private UserCouponModel fixedCoupon(ZonedDateTime expiredAt) {
        return new UserCouponModel(1L, 100L, "천원 할인", CouponType.FIXED, 1_000L, null, expiredAt);
    }

    @DisplayName("발급할 때,")
    @Nested
    class Issue {

        @DisplayName("AVAILABLE 상태로 생성된다.")
        @Test
        void createsAvailable_whenIssued() {
            UserCouponModel uc = fixedCoupon(FUTURE);
            assertThat(uc.getStatus()).isEqualTo(CouponStatus.AVAILABLE);
            assertThat(uc.getUserId()).isEqualTo(1L);
        }

        @DisplayName("템플릿의 혜택(이름/타입/값/최소금액/만료)이 스냅샷으로 복사된다.")
        @Test
        void snapshotsBenefitFromTemplate() {
            CouponModel template = new CouponModel("1만원 할인", CouponType.FIXED, 10_000, 50_000L, FUTURE);

            UserCouponModel uc = UserCouponModel.issue(1L, template);

            assertAll(
                () -> assertThat(uc.getCouponName()).isEqualTo("1만원 할인"),
                () -> assertThat(uc.getType()).isEqualTo(CouponType.FIXED),
                () -> assertThat(uc.getValue()).isEqualTo(10_000L),
                () -> assertThat(uc.getMinOrderAmount()).isEqualTo(50_000L),
                () -> assertThat(uc.getExpiredAt()).isEqualTo(FUTURE)
            );
        }

        @DisplayName("발급 후 템플릿이 수정되어도 발급분의 혜택은 변하지 않는다 (발급은 그 시점의 약속).")
        @Test
        void benefitUnaffected_whenTemplateUpdatedAfterIssue() {
            CouponModel template = new CouponModel("1만원 할인", CouponType.FIXED, 10_000, null, FUTURE);
            UserCouponModel uc = UserCouponModel.issue(1L, template);

            // 어드민이 템플릿 혜택을 대폭 축소
            template.update("100원 할인", CouponType.FIXED, 100, 999_999L, PAST, null);

            assertAll(
                () -> assertThat(uc.getCouponName()).isEqualTo("1만원 할인"),
                () -> assertThat(uc.getValue()).isEqualTo(10_000L),
                () -> assertThat(uc.getMinOrderAmount()).isZero(),
                () -> assertThat(uc.getExpiredAt()).isEqualTo(FUTURE),
                () -> assertThat(uc.calculateDiscount(Money.of(50_000L)).getAmount()).isEqualTo(10_000L)
            );
        }
    }

    @DisplayName("할인 금액을 계산할 때 (스냅샷 기준),")
    @Nested
    class CalculateDiscount {

        @DisplayName("정액 쿠폰은 고정 금액을 할인한다.")
        @Test
        void fixedDiscount() {
            UserCouponModel uc = new UserCouponModel(1L, 100L, "3천원 할인", CouponType.FIXED, 3_000L, null, FUTURE);
            Money discount = uc.calculateDiscount(Money.of(10_000L));
            assertThat(discount.getAmount()).isEqualTo(3_000L);
        }

        @DisplayName("정액 쿠폰 할인액이 주문금액을 초과하면 주문금액까지만 할인한다 (음수 결제 방지).")
        @Test
        void fixedDiscount_cappedAtOrderAmount() {
            UserCouponModel uc = new UserCouponModel(1L, 100L, "만원 할인", CouponType.FIXED, 10_000L, null, FUTURE);
            Money discount = uc.calculateDiscount(Money.of(3_000L));
            assertThat(discount.getAmount()).isEqualTo(3_000L);
        }

        @DisplayName("정률 쿠폰은 비율만큼 내림으로 할인한다.")
        @Test
        void rateDiscount() {
            UserCouponModel uc = new UserCouponModel(1L, 100L, "10% 할인", CouponType.RATE, 10L, null, FUTURE);
            Money discount = uc.calculateDiscount(Money.of(33_333L));
            assertThat(discount.getAmount()).isEqualTo(3_333L);   // 3,333.3 내림
        }
    }

    @DisplayName("적용 가능 여부를 검증할 때 (스냅샷 기준),")
    @Nested
    class ValidateApplicable {

        @DisplayName("만료된 쿠폰이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenExpired() {
            UserCouponModel uc = fixedCoupon(PAST);
            CoreException result = assertThrows(CoreException.class,
                () -> uc.validateApplicable(Money.of(10_000L), NOW));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("최소 주문 금액 미달이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBelowMinOrderAmount() {
            UserCouponModel uc = new UserCouponModel(1L, 100L, "조건부", CouponType.FIXED, 1_000L, 10_000L, FUTURE);
            CoreException result = assertThrows(CoreException.class,
                () -> uc.validateApplicable(Money.of(9_999L), NOW));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("유효하고 최소 금액 조건을 충족하면 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenApplicable() {
            UserCouponModel uc = new UserCouponModel(1L, 100L, "조건부", CouponType.FIXED, 1_000L, 10_000L, FUTURE);
            uc.validateApplicable(Money.of(10_000L), NOW);
        }
    }

    @DisplayName("사용할 때,")
    @Nested
    class Use {

        @DisplayName("AVAILABLE 쿠폰은 USED 로 전이된다.")
        @Test
        void transitionsToUsed_whenAvailable() {
            UserCouponModel uc = fixedCoupon(FUTURE);
            uc.use(NOW);
            assertThat(uc.getStatus()).isEqualTo(CouponStatus.USED);
            assertThat(uc.getUsedAt()).isNotNull();
        }

        @DisplayName("이미 사용된 쿠폰을 다시 사용하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenAlreadyUsed() {
            UserCouponModel uc = fixedCoupon(FUTURE);
            uc.use(NOW);
            CoreException result = assertThrows(CoreException.class, () -> uc.use(NOW));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

    }

    @DisplayName("사용을 취소할 때 (결제 실패 보상),")
    @Nested
    class CancelUse {

        @DisplayName("USED 쿠폰은 AVAILABLE 로 되돌아가고 usedAt 이 초기화된다.")
        @Test
        void revertsToAvailable_whenUsed() {
            UserCouponModel uc = fixedCoupon(FUTURE);
            uc.use(NOW);

            uc.cancelUse();

            assertThat(uc.getStatus()).isEqualTo(CouponStatus.AVAILABLE);
            assertThat(uc.getUsedAt()).isNull();
        }

        @DisplayName("사용되지 않은 쿠폰을 취소하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNotUsed() {
            UserCouponModel uc = fixedCoupon(FUTURE);

            CoreException result = assertThrows(CoreException.class, uc::cancelUse);
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("소유자 확인은,")
    @Nested
    class Ownership {

        @DisplayName("발급받은 유저면 true, 아니면 false 를 반환한다.")
        @Test
        void returnsOwnership() {
            UserCouponModel uc = fixedCoupon(FUTURE);
            assertThat(uc.isOwnedBy(1L)).isTrue();
            assertThat(uc.isOwnedBy(2L)).isFalse();
        }
    }

    @DisplayName("표시 상태는,")
    @Nested
    class DisplayStatus {

        @DisplayName("미사용 + 미만료면 AVAILABLE.")
        @Test
        void returnsAvailable_whenUnusedAndNotExpired() {
            UserCouponModel uc = fixedCoupon(FUTURE);
            assertThat(uc.displayStatus(NOW)).isEqualTo(CouponStatus.AVAILABLE);
        }

        @DisplayName("미사용이지만 만료되었으면 EXPIRED.")
        @Test
        void returnsExpired_whenUnusedButExpired() {
            UserCouponModel uc = fixedCoupon(PAST);
            assertThat(uc.displayStatus(NOW)).isEqualTo(CouponStatus.EXPIRED);
        }

        @DisplayName("사용 완료면 만료 여부와 무관하게 USED.")
        @Test
        void returnsUsed_whenUsed() {
            UserCouponModel uc = fixedCoupon(FUTURE);
            uc.use(NOW);
            assertThat(uc.displayStatus(NOW)).isEqualTo(CouponStatus.USED);
        }
    }
}
