package com.loopers.infrastructure.queue;

import com.loopers.application.queue.WaitingQueueRepository;
import com.loopers.config.redis.RedisConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@RequiredArgsConstructor
@Repository
public class RedisWaitingQueueRepository implements WaitingQueueRepository {

    private static final String WAITING_QUEUE_KEY = "order:waiting-queue";
    private static final String WAITING_QUEUE_SEQUENCE_KEY = "order:waiting-queue:sequence";
    private static final String ENTRY_TOKEN_KEY_FORMAT = "order:entry-token:%s";
    private static final String ENTRY_TOKEN_CLAIM_KEY_FORMAT = "order:entry-token-claim:%s";
    private static final DefaultRedisScript<Long> ENQUEUE_IF_ABSENT_SCRIPT = new DefaultRedisScript<>(
        """
            if redis.call('ZSCORE', KEYS[1], ARGV[1]) then
                return 0
            end
            if redis.call('ZCARD', KEYS[1]) == 0 then
                redis.call('DEL', KEYS[2])
            end
            local sequence = redis.call('INCR', KEYS[2])
            redis.call('ZADD', KEYS[1], sequence, ARGV[1])
            return 1
            """,
        Long.class
    );
    private static final DefaultRedisScript<Long> CLAIM_ENTRY_TOKEN_SCRIPT = new DefaultRedisScript<>(
        """
            local token = redis.call('GET', KEYS[1])
            if not token or token ~= ARGV[1] then
                return 0
            end
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl <= 0 then
                return 0
            end
            local claimed = redis.call('SET', KEYS[2], ARGV[1], 'PX', ttl, 'NX')
            if claimed then
                return 1
            end
            return 0
            """,
        Long.class
    );
    private static final DefaultRedisScript<Long> COMPLETE_ENTRY_TOKEN_SCRIPT = new DefaultRedisScript<>(
        """
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then
                return 0
            end
            if redis.call('GET', KEYS[2]) ~= ARGV[1] then
                return 0
            end
            redis.call('DEL', KEYS[1], KEYS[2])
            return 1
            """,
        Long.class
    );
    private static final DefaultRedisScript<Long> RELEASE_ENTRY_TOKEN_CLAIM_SCRIPT = new DefaultRedisScript<>(
        """
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then
                return 0
            end
            redis.call('DEL', KEYS[1])
            return 1
            """,
        Long.class
    );

    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public boolean enqueueIfAbsent(String userLoginId) {
        Long added = redisTemplate.execute(
            ENQUEUE_IF_ABSENT_SCRIPT,
            List.of(WAITING_QUEUE_KEY, WAITING_QUEUE_SEQUENCE_KEY),
            userLoginId
        );
        return Long.valueOf(1L).equals(added);
    }

    @Override
    public Optional<Long> findRank(String userLoginId) {
        return Optional.ofNullable(redisTemplate.opsForZSet().rank(WAITING_QUEUE_KEY, userLoginId));
    }

    @Override
    public long countWaitingUsers() {
        Long count = redisTemplate.opsForZSet().zCard(WAITING_QUEUE_KEY);
        return count == null ? 0L : count;
    }

    @Override
    public List<String> popWaitingUsers(int count) {
        if (count <= 0) {
            return List.of();
        }
        return redisTemplate.opsForZSet().popMin(WAITING_QUEUE_KEY, count).stream()
            .map(ZSetOperations.TypedTuple::getValue)
            .filter(Objects::nonNull)
            .toList();
    }

    @Override
    public void issueEntryToken(String userLoginId, String token, Duration ttl) {
        redisTemplate.opsForValue().set(entryTokenKey(userLoginId), token, ttl);
    }

    @Override
    public Optional<String> findEntryToken(String userLoginId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(entryTokenKey(userLoginId)));
    }

    @Override
    public boolean claimEntryToken(String userLoginId, String token) {
        Long claimed = redisTemplate.execute(
            CLAIM_ENTRY_TOKEN_SCRIPT,
            List.of(entryTokenKey(userLoginId), entryTokenClaimKey(userLoginId)),
            token
        );
        return Long.valueOf(1L).equals(claimed);
    }

    @Override
    public boolean completeEntryToken(String userLoginId, String token) {
        Long completed = redisTemplate.execute(
            COMPLETE_ENTRY_TOKEN_SCRIPT,
            List.of(entryTokenKey(userLoginId), entryTokenClaimKey(userLoginId)),
            token
        );
        return Long.valueOf(1L).equals(completed);
    }

    @Override
    public boolean releaseEntryTokenClaim(String userLoginId, String token) {
        Long released = redisTemplate.execute(
            RELEASE_ENTRY_TOKEN_CLAIM_SCRIPT,
            List.of(entryTokenClaimKey(userLoginId)),
            token
        );
        return Long.valueOf(1L).equals(released);
    }

    private String entryTokenKey(String userLoginId) {
        return ENTRY_TOKEN_KEY_FORMAT.formatted(userLoginId);
    }

    private String entryTokenClaimKey(String userLoginId) {
        return ENTRY_TOKEN_CLAIM_KEY_FORMAT.formatted(userLoginId);
    }
}
