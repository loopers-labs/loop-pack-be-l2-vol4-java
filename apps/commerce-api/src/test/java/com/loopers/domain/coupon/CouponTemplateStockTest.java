package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponTemplateStockTest {

    private static final LocalDateTime FAR_FUTURE = LocalDateTime.of(2099, 12, 31, 0, 0);

    @DisplayName("무제한 (totalStock=null)")
    @Nested
    class Unlimited {
        @DisplayName("issueOne 을 반복해도 소진되지 않는다.")
        @Test
        void neverSoldOut() {
            CouponTemplate t = new CouponTemplate("무제한", CouponType.FIXED, 1000L, 0L, FAR_FUTURE, null);

            for (int i = 0; i < 1_000; i++) t.issueOne();

            assertThat(t.isSoldOut()).isFalse();
            assertThat(t.getIssuedCount()).isEqualTo(1_000L);
        }
    }

    @DisplayName("제한 (totalStock=N)")
    @Nested
    class Limited {
        @DisplayName("정원까지 issueOne 성공.")
        @Test
        void issuesUpToStock() {
            CouponTemplate t = new CouponTemplate("한정", CouponType.FIXED, 1000L, 0L, FAR_FUTURE, 3L);

            t.issueOne();
            t.issueOne();
            t.issueOne();

            assertThat(t.isSoldOut()).isTrue();
            assertThat(t.getIssuedCount()).isEqualTo(3L);
        }

        @DisplayName("정원 초과 시 CONFLICT.")
        @Test
        void throwsOnOverflow() {
            CouponTemplate t = new CouponTemplate("한정", CouponType.FIXED, 1000L, 0L, FAR_FUTURE, 1L);
            t.issueOne();

            assertThatThrownBy(t::issueOne)
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.CONFLICT);
        }

        @DisplayName("totalStock 음수 생성은 BAD_REQUEST.")
        @Test
        void negativeStockRejected() {
            assertThatThrownBy(() -> new CouponTemplate("이상", CouponType.FIXED, 1000L, 0L, FAR_FUTURE, -1L))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }
    }
}
