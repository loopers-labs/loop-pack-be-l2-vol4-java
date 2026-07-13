package com.loopers.infrastructure.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.queue.QueueRedisStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * {@link QueueRedisStore} 구현 — Lua 스크립트로 대기열/토큰 상태 전이를 원자적으로 처리한다.
 *
 * <p>마스터 커넥션(REDIS_TEMPLATE_MASTER)을 사용한다 — 대기열 순위/토큰 상태는 발급 직후 즉시
 * 정합성 있게 읽혀야 하는데(입장 직후 폴링, 락 직후 재확인 등), 복제 지연이 있는 레플리카를
 * 읽으면 "방금 발급받은 토큰이 없다"는 식의 유령 상태가 보일 수 있다.
 */
@Component
public class QueueRedisStoreImpl implements QueueRedisStore {

    private static final String ENTER_RESULT_ADMITTED = "ADMITTED";
    private static final String ENTER_RESULT_WAITING = "WAITING";

    private static final String TOKEN_VALID = "VALID";
    private static final String TOKEN_LOCKED = "LOCKED";

    private static final String LOCK_OK = "OK";
    private static final String LOCK_EXPIRED = "EXPIRED";
    private static final String LOCK_BUSY = "BUSY";

    /**
     * 입장 요청 — 이미 토큰 보유(ADMITTED) 우선 확인, 그 외엔 ZADD NX로 대기열 추가.
     * NX라서 중복 호출(더블클릭 등)이 기존 순위를 앞/뒤로 바꾸지 않는다.
     */
    private static final DefaultRedisScript<String> ENTER_SCRIPT = new DefaultRedisScript<>("""
        local waitingKey = KEYS[1]
        local tokenKey = KEYS[2]
        local userId = ARGV[1]
        local now = ARGV[2]

        if redis.call('EXISTS', tokenKey) == 1 then
            return 'ADMITTED'
        end
        redis.call('ZADD', waitingKey, 'NX', now, userId)
        return 'WAITING'
        """, String.class);

    /** 배치 입장 — ZPOPMIN(점수=입장시각 오름차순 = FIFO)으로 앞에서부터 최대 batchSize명을 뽑는다. */
    private static final DefaultRedisScript<List> ADMIT_SCRIPT = new DefaultRedisScript<>("""
        local waitingKey = KEYS[1]
        local batchSize = tonumber(ARGV[1])

        local raw = redis.call('ZPOPMIN', waitingKey, batchSize)
        local members = {}
        for i = 1, #raw, 2 do
            table.insert(members, raw[i])
        end
        return members
        """, List.class);

    private static final DefaultRedisScript<String> LOCK_SCRIPT = new DefaultRedisScript<>("""
        local tokenKey = KEYS[1]
        local state = redis.call('GET', tokenKey)
        if state == false then
            return 'EXPIRED'
        end
        if state == 'LOCKED' then
            return 'BUSY'
        end
        redis.call('SET', tokenKey, 'LOCKED', 'KEEPTTL')
        return 'OK'
        """, String.class);

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>("""
        local tokenKey = KEYS[1]
        if redis.call('GET', tokenKey) == 'LOCKED' then
            redis.call('SET', tokenKey, 'VALID', 'KEEPTTL')
            return 1
        end
        return 0
        """, Long.class);

    private final RedisTemplate<String, String> redisTemplate;

    public QueueRedisStoreImpl(@Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public EnterResult enter(Long productId, Long userId, long nowMillis) {
        String result = redisTemplate.execute(
            ENTER_SCRIPT,
            List.of(waitingKey(productId), tokenKey(productId, userId)),
            userId.toString(),
            Long.toString(nowMillis)
        );
        return switch (result == null ? "" : result) {
            case ENTER_RESULT_ADMITTED -> EnterResult.ADMITTED;
            case ENTER_RESULT_WAITING -> EnterResult.WAITING;
            default -> throw new IllegalStateException("Unexpected queue enter result: " + result);
        };
    }

    @Override
    public QueueStatus status(Long productId, Long userId) {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(tokenKey(productId, userId)))) {
            return new QueueStatus(QueueStatus.State.ADMITTED, null, waitingCount(productId));
        }
        Long rank = redisTemplate.opsForZSet().rank(waitingKey(productId), userId.toString());
        Long position = rank == null ? null : rank + 1;
        return new QueueStatus(QueueStatus.State.WAITING, position, waitingCount(productId));
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Long> admitBatch(Long productId, int batchSize) {
        List<String> members = redisTemplate.execute(
            ADMIT_SCRIPT,
            List.of(waitingKey(productId)),
            Integer.toString(batchSize)
        );
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        return members.stream().map(Long::parseLong).toList();
    }

    @Override
    public void issueToken(Long productId, Long userId, Duration ttl) {
        redisTemplate.opsForValue().set(tokenKey(productId, userId), TOKEN_VALID, ttl);
    }

    @Override
    public TokenLockResult tryLock(Long productId, Long userId) {
        String result = redisTemplate.execute(
            LOCK_SCRIPT,
            List.of(tokenKey(productId, userId))
        );
        return switch (result == null ? "" : result) {
            case LOCK_OK -> TokenLockResult.OK;
            case LOCK_EXPIRED -> TokenLockResult.EXPIRED;
            case LOCK_BUSY -> TokenLockResult.BUSY;
            default -> throw new IllegalStateException("Unexpected queue lock result: " + result);
        };
    }

    @Override
    public void unlock(Long productId, Long userId) {
        redisTemplate.execute(UNLOCK_SCRIPT, List.of(tokenKey(productId, userId)));
    }

    @Override
    public void consumeToken(Long productId, Long userId) {
        redisTemplate.delete(tokenKey(productId, userId));
    }

    @Override
    public void leave(Long productId, Long userId) {
        redisTemplate.opsForZSet().remove(waitingKey(productId), userId.toString());
    }

    private long waitingCount(Long productId) {
        Long count = redisTemplate.opsForZSet().zCard(waitingKey(productId));
        return count == null ? 0L : count;
    }

    private String waitingKey(Long productId) {
        return "queue:" + productId + ":waiting";
    }

    private String tokenKey(Long productId, Long userId) {
        return "queue:" + productId + ":token:" + userId;
    }
}
