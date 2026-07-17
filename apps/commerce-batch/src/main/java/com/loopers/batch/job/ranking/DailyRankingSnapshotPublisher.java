package com.loopers.batch.job.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.ranking.DailyRankingKey;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class DailyRankingSnapshotPublisher {
    private static final String MARKER = "__snapshot_marker__";
    private static final Duration BUILD_TTL = Duration.ofHours(1);
    private static final Duration SNAPSHOT_TTL = Duration.ofDays(2);
    private static final int ZADD_CHUNK_SIZE = 500;
    private static final DefaultRedisScript<Long> PUBLISH_SCRIPT = new DefaultRedisScript<>("""
        if not redis.call('ZSCORE', KEYS[1], ARGV[1]) then
            return -1
        end
        redis.call('RENAME', KEYS[1], KEYS[2])
        redis.call('ZREM', KEYS[2], ARGV[1])
        redis.call('EXPIRE', KEYS[2], ARGV[2])
        return redis.call('ZCARD', KEYS[2])
        """, Long.class);

    private final RedisTemplate<String, String> redisTemplate;

    public DailyRankingSnapshotPublisher(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    public long publish(LocalDate requestDate, long jobExecutionId, List<DailyRankingScore> scores) {
        String canonicalKey = DailyRankingKey.from(requestDate);
        String temporaryKey = canonicalKey + ":building:" + jobExecutionId;

        redisTemplate.delete(temporaryKey);
        redisTemplate.opsForZSet().add(temporaryKey, MARKER, 0.0);
        redisTemplate.expire(temporaryKey, BUILD_TTL);

        for (int offset = 0; offset < scores.size(); offset += ZADD_CHUNK_SIZE) {
            int end = Math.min(offset + ZADD_CHUNK_SIZE, scores.size());
            Set<TypedTuple<String>> tuples = new LinkedHashSet<>();
            for (DailyRankingScore score : scores.subList(offset, end)) {
                tuples.add(new DefaultTypedTuple<>(DailyRankingKey.member(score.productId()), score.score()));
            }
            redisTemplate.opsForZSet().add(temporaryKey, tuples);
            redisTemplate.expire(temporaryKey, BUILD_TTL);
        }

        Long publishedCount = redisTemplate.execute(
            PUBLISH_SCRIPT,
            List.of(temporaryKey, canonicalKey),
            MARKER,
            Long.toString(SNAPSHOT_TTL.toSeconds())
        );
        if (publishedCount == null || publishedCount < 0) {
            throw new IllegalStateException("랭킹 스냅샷 발행 결과를 확인할 수 없습니다.");
        }
        return publishedCount;
    }

    public record DailyRankingScore(Long productId, double score) {
    }
}
