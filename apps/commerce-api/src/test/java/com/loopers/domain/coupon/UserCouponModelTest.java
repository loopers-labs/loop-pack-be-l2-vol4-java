package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class UserCouponModelTest {

    @Nested
    @DisplayName("발급 (생성)")
    class Issue {

        @DisplayName("발급하면 미사용 상태이고 소유자·템플릿이 기록된다")
        @Test
        void given_validInput_when_issue_then_unused() {
            UserCouponModel uc = new UserCouponModel(100L, 42L);

            assertAll(
                    () -> assertThat(uc.getUserId()).isEqualTo(100L),
                    () -> assertThat(uc.getCouponId()).isEqualTo(42L),
                    () -> assertThat(uc.isUsed()).isFalse(),
                    () -> assertThat(uc.getIssuedAt()).isNotNull()
            );
        }

        @DisplayName("userId가 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void given_nullUserId_when_issue_then_throwsBadRequest() {
            CoreException result = assertThrows(CoreException.class, () -> new UserCouponModel(null, 42L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("couponId가 null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void given_nullCouponId_when_issue_then_throwsBadRequest() {
            CoreException result = assertThrows(CoreException.class, () -> new UserCouponModel(100L, null));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("사용 / 원복 (use / restore)")
    class UseAndRestore {

        @DisplayName("use() 하면 사용 완료(usedAt 기록) 상태가 된다")
        @Test
        void given_unused_when_use_then_used() {
            UserCouponModel uc = new UserCouponModel(100L, 42L);

            uc.use();

            assertAll(
                    () -> assertThat(uc.isUsed()).isTrue(),
                    () -> assertThat(uc.getUsedAt()).isNotNull()
            );
        }

        @DisplayName("이미 사용된 쿠폰을 다시 use() 하면 CONFLICT 예외가 발생한다 (재사용 방지)")
        @Test
        void given_used_when_use_then_throwsConflict() {
            UserCouponModel uc = new UserCouponModel(100L, 42L);
            uc.use();

            CoreException result = assertThrows(CoreException.class, uc::use);
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("사용된 쿠폰을 restore() 하면 다시 미사용이 되어 재사용할 수 있다")
        @Test
        void given_used_when_restore_then_reusable() {
            UserCouponModel uc = new UserCouponModel(100L, 42L);
            uc.use();

            uc.restore();

            assertAll(
                    () -> assertThat(uc.isUsed()).isFalse(),
                    () -> assertThat(uc.getUsedAt()).isNull()
            );
        }
    }

    @Nested
    @DisplayName("소유자 검증 (isOwnedBy)")
    class Ownership {

        @DisplayName("같은 userId면 true, 다른 userId면 false")
        @Test
        void given_userId_when_isOwnedBy_then_matches() {
            UserCouponModel uc = new UserCouponModel(100L, 42L);

            assertAll(
                    () -> assertThat(uc.isOwnedBy(100L)).isTrue(),
                    () -> assertThat(uc.isOwnedBy(200L)).isFalse()
            );
        }
    }

    @Nested
    @DisplayName("상태 파생 (resolveStatus)")
    class ResolveStatus {

        @DisplayName("미사용 + 미만료면 AVAILABLE")
        @Test
        void given_unusedNotExpired_when_resolve_then_available() {
            UserCouponModel uc = new UserCouponModel(100L, 42L);
            ZonedDateTime now = ZonedDateTime.now();

            assertThat(uc.resolveStatus(now, now.plusDays(1))).isEqualTo(UserCouponStatus.AVAILABLE);
        }

        @DisplayName("미사용 + 만료 시각 경과면 EXPIRED")
        @Test
        void given_unusedExpired_when_resolve_then_expired() {
            UserCouponModel uc = new UserCouponModel(100L, 42L);
            ZonedDateTime now = ZonedDateTime.now();

            assertThat(uc.resolveStatus(now, now.minusSeconds(1))).isEqualTo(UserCouponStatus.EXPIRED);
        }

        @DisplayName("사용됐으면 만료 시각과 무관하게 USED가 우선이다")
        @Test
        void given_usedAndExpired_when_resolve_then_used() {
            UserCouponModel uc = new UserCouponModel(100L, 42L);
            uc.use();
            ZonedDateTime now = ZonedDateTime.now();

            assertThat(uc.resolveStatus(now, now.minusSeconds(1))).isEqualTo(UserCouponStatus.USED);
        }
    }
}
