package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponTemplateModelTest {

    @DisplayName("쿠폰 템플릿을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("FIXED 타입, 유효한 값으로 생성하면 정상 생성된다.")
        @Test
        void create_fixedType_success() {
            // act
            CouponTemplateModel template = new CouponTemplateModel(
                "신규가입 쿠폰", CouponType.FIXED, 1000L, null, LocalDateTime.now().plusDays(7)
            );

            // assert
            assertThat(template.getName()).isEqualTo("신규가입 쿠폰");
            assertThat(template.getType()).isEqualTo(CouponType.FIXED);
            assertThat(template.getValue()).isEqualTo(1000L);
            assertThat(template.isActive()).isTrue();
        }

        @DisplayName("RATE 타입, 유효한 값으로 생성하면 정상 생성된다.")
        @Test
        void create_rateType_success() {
            // act
            CouponTemplateModel template = new CouponTemplateModel(
                "10% 할인 쿠폰", CouponType.RATE, 10L, null, LocalDateTime.now().plusDays(7)
            );

            // assert
            assertThat(template.getName()).isEqualTo("10% 할인 쿠폰");
            assertThat(template.getType()).isEqualTo(CouponType.RATE);
            assertThat(template.getValue()).isEqualTo(10L);
            assertThat(template.isActive()).isTrue();
        }

        @DisplayName("name이 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void create_nullName_throwsBadRequest() {
            assertThatThrownBy(() -> new CouponTemplateModel(
                null, CouponType.FIXED, 1000L, null, LocalDateTime.now().plusDays(7)
            ))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("name이 blank이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void create_blankName_throwsBadRequest() {
            assertThatThrownBy(() -> new CouponTemplateModel(
                "  ", CouponType.FIXED, 1000L, null, LocalDateTime.now().plusDays(7)
            ))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("FIXED 타입에서 value가 0 이하이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void create_fixedType_zeroOrNegativeValue_throwsBadRequest() {
            assertThatThrownBy(() -> new CouponTemplateModel(
                "쿠폰", CouponType.FIXED, 0L, null, LocalDateTime.now().plusDays(7)
            ))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("minOrderAmount가 음수이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void create_negativeMinOrderAmount_throwsBadRequest() {
            assertThatThrownBy(() -> new CouponTemplateModel(
                "쿠폰", CouponType.FIXED, 1000L, -1L, LocalDateTime.now().plusDays(7)
            ))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("RATE 타입에서 value가 0 이하이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void create_rateType_zeroOrNegativeValue_throwsBadRequest() {
            assertThatThrownBy(() -> new CouponTemplateModel(
                "쿠폰", CouponType.RATE, 0L, null, LocalDateTime.now().plusDays(7)
            ))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("RATE 타입에서 value가 101 이상이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void create_rateType_valueOver100_throwsBadRequest() {
            assertThatThrownBy(() -> new CouponTemplateModel(
                "쿠폰", CouponType.RATE, 101L, null, LocalDateTime.now().plusDays(7)
            ))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("expiredAt이 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void create_nullExpiredAt_throwsBadRequest() {
            assertThatThrownBy(() -> new CouponTemplateModel(
                "쿠폰", CouponType.FIXED, 1000L, null, null
            ))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("expiredAt이 과거이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void create_pastExpiredAt_throwsBadRequest() {
            assertThatThrownBy(() -> new CouponTemplateModel(
                "쿠폰", CouponType.FIXED, 1000L, null, LocalDateTime.now().minusDays(1)
            ))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("쿠폰 템플릿을 수정할 때,")
    @Nested
    class Update {

        @DisplayName("name과 isActive를 수정하면 반영된다.")
        @Test
        void update_nameAndIsActive_success() {
            // arrange
            CouponTemplateModel template = new CouponTemplateModel(
                "기존 쿠폰", CouponType.FIXED, 1000L, null, LocalDateTime.now().plusDays(7)
            );

            // act
            template.update("수정된 쿠폰", false);

            // assert
            assertThat(template.getName()).isEqualTo("수정된 쿠폰");
            assertThat(template.isActive()).isFalse();
        }
    }

    @DisplayName("만료 여부를 확인할 때,")
    @Nested
    class IsExpired {

        @DisplayName("expiredAt이 과거이면 isExpired()가 true를 반환한다.")
        @Test
        void isExpired_pastExpiredAt_returnsTrue() {
            // arrange — 생성 후 expiredAt을 과거로 변경 (생성자 검증 우회)
            CouponTemplateModel template = new CouponTemplateModel(
                "쿠폰", CouponType.FIXED, 1000L, null, LocalDateTime.now().plusDays(7)
            );
            ReflectionTestUtils.setField(template, "expiredAt", LocalDateTime.now().minusDays(1));

            // assert
            assertThat(template.isExpired()).isTrue();
        }

        @DisplayName("expiredAt이 미래이면 isExpired()가 false를 반환한다.")
        @Test
        void isExpired_futureExpiredAt_returnsFalse() {
            // arrange
            CouponTemplateModel template = new CouponTemplateModel(
                "쿠폰", CouponType.FIXED, 1000L, null, LocalDateTime.now().plusDays(7)
            );

            // assert
            assertThat(template.isExpired()).isFalse();
        }
    }

    @DisplayName("발급 가능 여부를 확인할 때,")
    @Nested
    class CanIssue {

        @DisplayName("isActive=true이고 미만료이면 canIssue()가 true를 반환한다.")
        @Test
        void canIssue_activeAndNotExpired_returnsTrue() {
            // arrange
            CouponTemplateModel template = new CouponTemplateModel(
                "쿠폰", CouponType.FIXED, 1000L, null, LocalDateTime.now().plusDays(7)
            );

            // assert
            assertThat(template.canIssue()).isTrue();
        }

        @DisplayName("isActive=false이면 canIssue()가 false를 반환한다.")
        @Test
        void canIssue_inactive_returnsFalse() {
            // arrange
            CouponTemplateModel template = new CouponTemplateModel(
                "쿠폰", CouponType.FIXED, 1000L, null, LocalDateTime.now().plusDays(7)
            );
            template.update("쿠폰", false);

            // assert
            assertThat(template.canIssue()).isFalse();
        }

        @DisplayName("만료되었으면 isActive=true여도 canIssue()가 false를 반환한다.")
        @Test
        void canIssue_expired_returnsFalse_evenIfActive() {
            // arrange
            CouponTemplateModel template = new CouponTemplateModel(
                "쿠폰", CouponType.FIXED, 1000L, null, LocalDateTime.now().plusDays(7)
            );
            ReflectionTestUtils.setField(template, "expiredAt", LocalDateTime.now().minusDays(1));

            // assert
            assertThat(template.isActive()).isTrue();
            assertThat(template.canIssue()).isFalse();
        }
    }
}
