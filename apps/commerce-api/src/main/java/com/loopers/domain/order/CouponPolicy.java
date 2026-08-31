package com.loopers.domain.order;
import java.time.Instant;
public interface CouponPolicy { long discount(long buyerId, long couponId, long originalAmount, Instant requestStartedAt); }
