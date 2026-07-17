package com.loopers.infrastructure.waitingqueue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.waitingqueue.EntryTokenRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 입장 토큰(pass/user-pass String+TTL) + 활성 세트(active:users ZSET) 어댑터.
 * 토큰 검증·활성 카운트는 정합성이 중요하므로 마스터 템플릿으로만 조작한다(04 §0·§2.5).
 */
@Repository
public class RedisEntryTokenRepository implements EntryTokenRepository {

    private static final String PASS_PREFIX = "pass:";
    private static final String USER_PASS_PREFIX = "user-pass:";
    private static final String ACTIVE_KEY = "active:users";

    private final RedisTemplate<String, String> redis;
    private final ZSetOperations<String, String> zset;
    private final ValueOperations<String, String> value;

    public RedisEntryTokenRepository(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate
    ) {
        this.redis = masterRedisTemplate;
        this.zset = masterRedisTemplate.opsForZSet();
        this.value = masterRedisTemplate.opsForValue();
    }

    @Override
    public boolean isActive(Long userId) {
        Double expireAt = zset.score(ACTIVE_KEY, userId.toString());
        return expireAt != null && expireAt > now();
    }

    @Override
    public long activeCountLive() {
        Long count = zset.count(ACTIVE_KEY, now(), Double.POSITIVE_INFINITY);
        return count == null ? 0L : count;
    }

    @Override
    public Long findUserIdByToken(String token) {
        String userId = value.get(passKey(token));
        return userId == null ? null : Long.valueOf(userId);
    }

    @Override
    public String findTokenByUser(Long userId) {
        return value.get(userPassKey(userId));
    }

    @Override
    public void consume(Long userId, String token) {
        redis.delete(List.of(passKey(token), userPassKey(userId)));
        zset.remove(ACTIVE_KEY, userId.toString());
    }

    private String passKey(String token) {
        return PASS_PREFIX + token;
    }

    private String userPassKey(Long userId) {
        return USER_PASS_PREFIX + userId;
    }

    private long now() {
        return System.currentTimeMillis();
    }
}
