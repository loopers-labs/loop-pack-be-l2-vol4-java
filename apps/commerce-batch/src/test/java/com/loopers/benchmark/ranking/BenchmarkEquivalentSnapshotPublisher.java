package com.loopers.benchmark.ranking;

import com.loopers.batch.job.ranking.DailyRankingSnapshotPublisher.DailyRankingScore;
import com.loopers.ranking.DailyRankingKey;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class BenchmarkEquivalentSnapshotPublisher {
    static final String PUBLISHER_SCOPE = "benchmark-equivalent";
    static final int PRODUCTION_CHUNK_SIZE = 500;
    private static final String MARKER = "__snapshot_marker__";
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

    BenchmarkEquivalentSnapshotPublisher(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    PublishOutcome publish(
        LocalDate date,
        long executionId,
        List<DailyRankingScore> scores,
        int chunkSize,
        int failAfterChunks
    ) {
        String canonicalKey = DailyRankingKey.from(date);
        String temporaryKey = canonicalKey + ":benchmark-building:" + executionId;
        redisTemplate.delete(temporaryKey);
        redisTemplate.opsForZSet().add(temporaryKey, MARKER, 0.0);
        redisTemplate.expire(temporaryKey, java.time.Duration.ofHours(1));

        long chunks = 0;
        for (int offset = 0; offset < scores.size(); offset += chunkSize) {
            int end = Math.min(offset + chunkSize, scores.size());
            Set<TypedTuple<String>> tuples = new LinkedHashSet<>();
            for (DailyRankingScore score : scores.subList(offset, end)) {
                tuples.add(new DefaultTypedTuple<>(DailyRankingKey.member(score.productId()), score.score()));
            }
            redisTemplate.opsForZSet().add(temporaryKey, tuples);
            redisTemplate.expire(temporaryKey, java.time.Duration.ofHours(1));
            chunks++;
            if (failAfterChunks >= 0 && chunks >= failAfterChunks) {
                throw new InjectedBuildFailure(temporaryKey, chunks);
            }
        }

        Long count = redisTemplate.execute(
            PUBLISH_SCRIPT,
            List.of(temporaryKey, canonicalKey),
            MARKER,
            Long.toString(java.time.Duration.ofDays(2).toSeconds())
        );
        if (count == null || count < 0) {
            throw new IllegalStateException("ranking snapshot publish result is unavailable");
        }
        return new PublishOutcome(count, chunks);
    }

    record PublishOutcome(long memberMutations, long chunkCount) {
    }

    static final class InjectedBuildFailure extends RuntimeException {
        private final String temporaryKey;
        private final long completedChunks;

        InjectedBuildFailure(String temporaryKey, long completedChunks) {
            super("injected failure after " + completedChunks + " chunks");
            this.temporaryKey = temporaryKey;
            this.completedChunks = completedChunks;
        }

        String temporaryKey() {
            return temporaryKey;
        }

        long completedChunks() {
            return completedChunks;
        }
    }
}
