package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserCouponModelTest {

    @DisplayName("유저 쿠폰을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 memberId, templateId로 생성하면 usedAt=null 상태로 생성된다.")
        @Test
        void create_success() {
            // act
            UserCouponModel userCoupon = new UserCouponModel(1L, 10L);

            // assert
            assertThat(userCoupon.getMemberId()).isEqualTo(1L);
            assertThat(userCoupon.getTemplateId()).isEqualTo(10L);
            assertThat(userCoupon.getUsedAt()).isNull();
        }

        @DisplayName("memberId가 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void create_nullMemberId_throwsBadRequest() {
            assertThatThrownBy(() -> new UserCouponModel(null, 10L))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("templateId가 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void create_nullTemplateId_throwsBadRequest() {
            assertThatThrownBy(() -> new UserCouponModel(1L, null))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("쿠폰을 사용할 때,")
    @Nested
    class Use {

        @DisplayName("use()를 호출하면 usedAt이 설정된다.")
        @Test
        void use_success() {
            // arrange
            UserCouponModel userCoupon = new UserCouponModel(1L, 10L);

            // act
            userCoupon.use();

            // assert
            assertThat(userCoupon.getUsedAt()).isNotNull();
        }

        @DisplayName("이미 사용된 쿠폰에 use()를 호출하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void use_alreadyUsed_throwsBadRequest() {
            // arrange
            UserCouponModel userCoupon = new UserCouponModel(1L, 10L);
            userCoupon.use();

            // act & assert
            assertThatThrownBy(userCoupon::use)
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

    }

    @DisplayName("쿠폰 상태를 조회할 때,")
    @Nested
    class GetStatus {

        @DisplayName("usedAt=null이고 미만료이면 getStatus()가 AVAILABLE을 반환한다.")
        @Test
        void getStatus_available() {
            // arrange
            UserCouponModel userCoupon = new UserCouponModel(1L, 10L);
            LocalDateTime futureExpiredAt = LocalDateTime.now().plusDays(7);

            // assert
            assertThat(userCoupon.getStatus(futureExpiredAt, false)).isEqualTo(CouponStatus.AVAILABLE);
        }

        @DisplayName("usedAt이 설정되어 있으면 getStatus()가 USED를 반환한다.")
        @Test
        void getStatus_used() {
            // arrange
            UserCouponModel userCoupon = new UserCouponModel(1L, 10L);
            userCoupon.use();
            LocalDateTime futureExpiredAt = LocalDateTime.now().plusDays(7);

            // assert
            assertThat(userCoupon.getStatus(futureExpiredAt, false)).isEqualTo(CouponStatus.USED);
        }

        @DisplayName("expiredAt이 과거이면 getStatus()가 EXPIRED를 반환한다.")
        @Test
        void getStatus_expired() {
            // arrange
            UserCouponModel userCoupon = new UserCouponModel(1L, 10L);
            userCoupon.use();
            LocalDateTime pastExpiredAt = LocalDateTime.now().minusDays(1);

            // assert — 만료가 사용보다 우선
            assertThat(userCoupon.getStatus(pastExpiredAt, false)).isEqualTo(CouponStatus.EXPIRED);
        }

        @DisplayName("템플릿이 차단되면 getStatus()가 BLOCKED를 반환한다.")
        @Test
        void getStatus_blocked() {
            // arrange
            UserCouponModel userCoupon = new UserCouponModel(1L, 10L);
            LocalDateTime futureExpiredAt = LocalDateTime.now().plusDays(7);

            // assert
            assertThat(userCoupon.getStatus(futureExpiredAt, true)).isEqualTo(CouponStatus.BLOCKED);
        }
    }
}
