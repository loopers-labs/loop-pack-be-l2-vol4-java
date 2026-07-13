package com.loopers.infrastructure.coupon;

import com.loopers.application.coupon.CouponIssueRedisStore;
import com.loopers.config.redis.RedisConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class CouponIssueRedisStoreImpl implements CouponIssueRedisStore {

    private static final String RESERVED_CODE = "RESERVED";
    private static final String DUPLICATE_CODE = "DUPLICATE";
    private static final String SOLD_OUT_CODE = "SOLD_OUT";

    private static final DefaultRedisScript<String> RESERVE_SCRIPT = new DefaultRedisScript<>("""
        local issuedKey = KEYS[1]
        local reservedKey = KEYS[2]
        local requestKey = KEYS[3]
        local userId = ARGV[1]
        local totalQuantity = tonumber(ARGV[2])
        local requestId = ARGV[3]
        local now = ARGV[4]

        if redis.call('SISMEMBER', issuedKey, userId) == 1 then
            return 'DUPLICATE'
        end
        if redis.call('ZSCORE', reservedKey, userId) ~= false then
            return 'DUPLICATE'
        end

        if totalQuantity >= 0 then
            local issuedCount = redis.call('SCARD', issuedKey)
            local reservedCount = redis.call('ZCARD', reservedKey)
            if issuedCount + reservedCount >= totalQuantity then
                return 'SOLD_OUT'
            end
        end

        redis.call('ZADD', reservedKey, now, userId)
        redis.call('SET', requestKey, requestId)
        return 'RESERVED'
        """, String.class);

    private static final DefaultRedisScript<Long> CONFIRM_SCRIPT = new DefaultRedisScript<>("""
        local issuedKey = KEYS[1]
        local reservedKey = KEYS[2]
        local requestKey = KEYS[3]
        local userId = ARGV[1]
        local requestId = ARGV[2]

        if redis.call('SISMEMBER', issuedKey, userId) == 1 then
            return 1
        end

        -- 예약이 이미 취소(cancel)되어 사라졌으면 재확정하지 않는다.
        -- (재전달/리밸런싱으로 confirm 이 cancel 보다 늦게 도착하는 경합을 차단)
        if redis.call('ZSCORE', reservedKey, userId) == false then
            return 0
        end

        local storedRequestId = redis.call('GET', requestKey)
        if storedRequestId ~= false and storedRequestId ~= requestId then
            return 0
        end

        redis.call('SADD', issuedKey, userId)
        redis.call('ZREM', reservedKey, userId)
        redis.call('DEL', requestKey)
        return 1
        """, Long.class);

    private static final DefaultRedisScript<Long> CANCEL_SCRIPT = new DefaultRedisScript<>("""
        local reservedKey = KEYS[1]
        local requestKey = KEYS[2]
        local userId = ARGV[1]
        local requestId = ARGV[2]

        local storedRequestId = redis.call('GET', requestKey)
        if storedRequestId == requestId then
            redis.call('ZREM', reservedKey, userId)
            redis.call('DEL', requestKey)
            return 1
        end
        return 0
        """, Long.class);

    private final RedisTemplate<String, String> redisTemplate;

    public CouponIssueRedisStoreImpl(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public ReservationResult reserve(Long couponId, Long userId, Integer totalQuantity, String requestId) {
        String result = redisTemplate.execute(
            RESERVE_SCRIPT,
            List.of(issuedKey(couponId), reservedKey(couponId), requestKey(couponId, userId)),
            userId.toString(),
            totalQuantity == null ? "-1" : totalQuantity.toString(),
            requestId,
            Long.toString(Instant.now().toEpochMilli())
        );
        if (RESERVED_CODE.equals(result)) {
            return ReservationResult.RESERVED;
        }
        if (DUPLICATE_CODE.equals(result)) {
            return ReservationResult.DUPLICATE;
        }
        if (SOLD_OUT_CODE.equals(result)) {
            return ReservationResult.SOLD_OUT;
        }
        throw new IllegalStateException("Unexpected coupon issue reservation result: " + result);
    }

    @Override
    public void confirmIssue(Long couponId, Long userId, String requestId) {
        redisTemplate.execute(
            CONFIRM_SCRIPT,
            List.of(issuedKey(couponId), reservedKey(couponId), requestKey(couponId, userId)),
            userId.toString(),
            requestId
        );
    }

    @Override
    public void cancelReservation(Long couponId, Long userId, String requestId) {
        redisTemplate.execute(
            CANCEL_SCRIPT,
            List.of(reservedKey(couponId), requestKey(couponId, userId)),
            userId.toString(),
            requestId
        );
    }

    private String issuedKey(Long couponId) {
        return "coupon:issue:" + couponId + ":issued";
    }

    private String reservedKey(Long couponId) {
        return "coupon:issue:" + couponId + ":reserved";
    }

    private String requestKey(Long couponId, Long userId) {
        return "coupon:issue:" + couponId + ":request:" + userId;
    }
}
