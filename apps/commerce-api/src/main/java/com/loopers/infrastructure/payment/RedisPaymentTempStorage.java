package com.loopers.infrastructure.payment;

import com.loopers.application.payment.PaymentTempStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RedisPaymentTempStorage implements PaymentTempStorage {

    private final RedisTemplate<String, String> defaultRedisTemplate;

    @Override
    public void setRetryCount(Long paymentId, int count, Duration ttl) {
        String key = "payment_retry:" + paymentId;
        defaultRedisTemplate.opsForValue().set(key, String.valueOf(count), ttl);
    }

    @Override
    public void deleteRetryKey(Long paymentId) {
        String key = "payment_retry:" + paymentId;
        defaultRedisTemplate.delete(key);
    }

    @Override
    public Integer getRetryCount(Long paymentId) {
        String key = "payment_retry:" + paymentId;
        String val = defaultRedisTemplate.opsForValue().get(key);
        return val != null ? Integer.parseInt(val) : null;
    }
}
