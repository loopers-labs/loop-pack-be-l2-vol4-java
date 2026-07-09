package com.loopers.infrastructure.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.queue.EntryTokenRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * String 키 기반 입장 토큰 ({@code entry-token:{userId}}, TTL 은 Redis EX 로 위임).
 *
 * <p>만료를 애플리케이션 타이머가 아닌 Redis TTL 에 맡기므로 별도 청소 작업이 없다.
 * 토큰 검증 직후 만료되는 경계는 있지만, 검증~주문 사이의 찰나이므로 허용한다.</p>
 *
 * <p>master 템플릿 사용 — 발급(SET) 직후 폴링 응답(GET)이 replica lag 로 토큰을 못 보면
 * 유저가 입장 시점을 놓친다({@link WaitingQueueRedisRepository} 와 동일한 근거).</p>
 */
@Component
public class EntryTokenRedisRepository implements EntryTokenRepository {

    private static final String KEY_PREFIX = "entry-token:";
    private static final String IN_FLIGHT_PREFIX = "processing-token:";

    /** 값이 내 ownerToken 과 일치할 때만 DEL (compare-and-delete). 조회~삭제를 원자적으로 묶어 소유권 경합을 막는다. */
    private static final RedisScript<Long> RELEASE_IF_OWNER = RedisScript.of(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final RedisTemplate<String, String> redisTemplate;

    public EntryTokenRedisRepository(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void issue(Long userId, String token, Duration ttl) {
        redisTemplate.opsForValue().set(key(userId), token, ttl);
    }

    @Override
    public Optional<String> find(Long userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(userId)));
    }

    @Override
    public void delete(Long userId) {
        redisTemplate.delete(key(userId));
    }

    @Override
    public boolean acquireInFlight(Long userId, String ownerToken, Duration ttl) {
        // SET processing-token:{userId} <ownerToken> NX EX — 선점과 TTL 을 한 명령으로 원자화.
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(inFlightKey(userId), ownerToken, ttl);
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public void releaseInFlight(Long userId, String ownerToken) {
        // compare-and-delete — 저장된 값이 내 ownerToken 일 때만 삭제(만료 후 새 소유자 마크 보호).
        redisTemplate.execute(RELEASE_IF_OWNER, List.of(inFlightKey(userId)), ownerToken);
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }

    private String inFlightKey(Long userId) {
        return IN_FLIGHT_PREFIX + userId;
    }
}
