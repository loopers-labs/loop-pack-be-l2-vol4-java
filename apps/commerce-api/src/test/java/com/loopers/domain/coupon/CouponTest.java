package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponTest {

    private static final ZonedDateTime NOW = ZonedDateTime.now();

    private Coupon coupon(ZonedDateTime expiredAt) {
        return new Coupon("신규가입 10% 할인", CouponType.RATE, 10L, 10000L, expiredAt);
    }

    @DisplayName("발급하면, 발급 시점 정책이 스냅샷으로 복사된 UserCoupon 이 생성된다.")
    @Test
    void issueTo_copiesSnapshot() {
        // arrange
        Coupon coupon = coupon(NOW.plusDays(30));

        // act
        UserCoupon issued = coupon.issueTo(1L, NOW);

        // assert
        assertAll(
            () -> assertThat(issued.getUserId()).isEqualTo(1L),
            () -> assertThat(issued.getSnapshot())
                .isEqualTo(new CouponSnapshot("신규가입 10% 할인", CouponType.RATE, 10L, 10000L)),
            () -> assertThat(issued.getStatus()).isEqualTo(UserCouponStatus.AVAILABLE),
            () -> assertThat(issued.getIssuedAt()).isEqualTo(NOW),
            () -> assertThat(issued.getExpiredAt()).isEqualTo(coupon.getExpiredAt())
        );
    }

    @DisplayName("만료된 쿠폰은 발급할 수 없다.")
    @Test
    void issueTo_throws_whenExpired() {
        // arrange
        Coupon coupon = coupon(NOW.plusDays(1));

        // act & assert
        assertThrows(CoreException.class, () -> coupon.issueTo(1L, NOW.plusDays(2)));
    }

    @DisplayName("삭제된 쿠폰은 발급할 수 없다.")
    @Test
    void issueTo_throws_whenDeleted() {
        // arrange
        Coupon coupon = coupon(NOW.plusDays(30));
        coupon.delete();

        // act & assert
        assertThrows(CoreException.class, () -> coupon.issueTo(1L, NOW));
    }

    @DisplayName("만료일이 없으면 생성할 수 없다.")
    @Test
    void throws_whenExpiredAtNull() {
        // act & assert
        assertThrows(CoreException.class, () -> coupon(null));
    }

    @DisplayName("수정하면, 정책 필드가 갱신된다.")
    @Test
    void updates() {
        // arrange
        Coupon coupon = coupon(NOW.plusDays(30));

        // act
        coupon.update("5천원 할인", CouponType.FIXED, 5000L, null, NOW.plusDays(60));

        // assert
        assertAll(
            () -> assertThat(coupon.getName()).isEqualTo("5천원 할인"),
            () -> assertThat(coupon.getType()).isEqualTo(CouponType.FIXED),
            () -> assertThat(coupon.getValue()).isEqualTo(5000L),
            () -> assertThat(coupon.getMinOrderAmount()).isNull(),
            () -> assertThat(coupon.getExpiredAt()).isEqualTo(NOW.plusDays(60))
        );
    }
}
