package com.loopers.infrastructure.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.queue.EntryTokenRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * 입장 토큰 Redis 어댑터 — STRING(SET EX / GET / DEL). 만료는 Redis TTL 에 위임한다.
 * 발급 직후 게이트 검증이 이어지므로 전 연산을 master 템플릿으로 고정한다(복제 지연 회피).
 */
@Component
public class EntryTokenRepositoryImpl implements EntryTokenRepository {

    private static final String KEY_PREFIX = "queue:order:token:";

    private final RedisTemplate<String, String> redisTemplate;

    public EntryTokenRepositoryImpl(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void issue(String loginId, String token, Duration ttl) {
        redisTemplate.opsForValue().set(key(loginId), token, ttl);
    }

    @Override
    public Optional<String> find(String loginId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(loginId)));
    }

    @Override
    public void delete(String loginId) {
        redisTemplate.delete(key(loginId));
    }

    private String key(String loginId) {
        return KEY_PREFIX + loginId;
    }
}
