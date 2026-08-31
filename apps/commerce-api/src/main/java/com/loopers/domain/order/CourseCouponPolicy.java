package com.loopers.domain.order;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;
import java.time.Instant;
// Hides: the learner fixture's coupon ownership, rate, and request-start expiry decision.
@Component
public class CourseCouponPolicy implements CouponPolicy {
    static final Instant EXPIRES_AT = Instant.parse("2026-09-01T00:00:00Z");
    public long discount(long buyerId, long couponId, long originalAmount, Instant startedAt) {
        if (buyerId <= 0 || couponId != 10L || startedAt == null || !startedAt.isBefore(EXPIRES_AT))
            throw new CoreException(ErrorType.BAD_REQUEST, "coupon unavailable");
        return originalAmount / 10;
    }
}
