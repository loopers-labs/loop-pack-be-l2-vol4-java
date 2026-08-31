package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderDiscountTest {
    private static final long BUYER_ID = 135135L;
    private static final long COUPON_ID = 10L;
    private static final Instant REQUEST_STARTED_AT = Instant.parse("2026-08-31T14:59:59Z");

    @Test
    void storesTheConfirmedDiscountSnapshot() {
        Order order = new Order(BUYER_ID, 10_000L);

        order.applyDiscount(COUPON_ID, 1_000L, REQUEST_STARTED_AT);
        order.confirm();

        assertAll(
            () -> assertThat(order.getBuyerId()).isEqualTo(BUYER_ID),
            () -> assertThat(order.getOriginalAmount()).isEqualTo(10_000L),
            () -> assertThat(order.getDiscountAmount()).isEqualTo(1_000L),
            () -> assertThat(order.getFinalAmount()).isEqualTo(9_000L),
            () -> assertThat(order.getAppliedCouponId()).isEqualTo(COUPON_ID),
            () -> assertThat(order.getDiscountAppliedAt()).isEqualTo(REQUEST_STARTED_AT),
            () -> assertThat(order.isConfirmed()).isTrue()
        );
    }

    @Test
    void allowsZeroAndFullDiscountBoundaries() {
        Order zeroDiscount = new Order(BUYER_ID, 10_000L);
        Order fullDiscount = new Order(BUYER_ID, 10_000L);

        zeroDiscount.applyDiscount(COUPON_ID, 0L, REQUEST_STARTED_AT);
        fullDiscount.applyDiscount(COUPON_ID, 10_000L, REQUEST_STARTED_AT);

        assertAll(
            () -> assertThat(zeroDiscount.getFinalAmount()).isEqualTo(10_000L),
            () -> assertThat(fullDiscount.getFinalAmount()).isZero()
        );
    }

    @Test
    void rejectsNegativeAndExcessDiscountBoundaries() {
        Order order = new Order(BUYER_ID, 10_000L);

        CoreException negative = assertThrows(
            CoreException.class,
            () -> order.applyDiscount(COUPON_ID, -1L, REQUEST_STARTED_AT)
        );
        CoreException excess = assertThrows(
            CoreException.class,
            () -> order.applyDiscount(COUPON_ID, 10_001L, REQUEST_STARTED_AT)
        );

        assertAll(
            () -> assertThat(negative.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
            () -> assertThat(excess.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST)
        );
    }

    @Test
    void sameCouponRetryDoesNotAccumulateDiscount() {
        Order order = new Order(BUYER_ID, 10_000L);
        order.applyDiscount(COUPON_ID, 1_000L, REQUEST_STARTED_AT);

        order.applyDiscount(COUPON_ID, 2_000L, REQUEST_STARTED_AT.plusSeconds(1));

        assertAll(
            () -> assertThat(order.getDiscountAmount()).isEqualTo(1_000L),
            () -> assertThat(order.getFinalAmount()).isEqualTo(9_000L),
            () -> assertThat(order.getDiscountAppliedAt()).isEqualTo(REQUEST_STARTED_AT)
        );
    }

    @Test
    void rejectsASecondCouponAndAnyNewCouponAfterConfirmation() {
        Order order = new Order(BUYER_ID, 10_000L);
        order.applyDiscount(COUPON_ID, 1_000L, REQUEST_STARTED_AT);

        assertThrows(
            CoreException.class,
            () -> order.applyDiscount(11L, 1_000L, REQUEST_STARTED_AT)
        );

        order.confirm();

        CoreException confirmed = assertThrows(
            CoreException.class,
            () -> order.applyDiscount(12L, 1_000L, REQUEST_STARTED_AT)
        );
        assertThat(confirmed.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }
}
