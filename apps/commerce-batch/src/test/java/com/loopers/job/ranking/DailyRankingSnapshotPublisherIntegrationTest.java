package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.DailyRankingSnapshotPublisher;
import com.loopers.batch.job.ranking.DailyRankingSnapshotPublisher.DailyRankingScore;
import com.loopers.config.redis.RedisConfig;
import com.loopers.ranking.DailyRankingKey;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.Duration;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
class DailyRankingSnapshotPublisherIntegrationTest {

    private final DailyRankingSnapshotPublisher publisher;
    private final RedisTemplate<String, String> redisTemplate;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    DailyRankingSnapshotPublisherIntegrationTest(
        DailyRankingSnapshotPublisher publisher,
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate,
        RedisCleanUp redisCleanUp
    ) {
        this.publisher = publisher;
        this.redisTemplate = redisTemplate;
        this.redisCleanUp = redisCleanUp;
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("완성된 임시 ZSET을 canonical 키로 교체하며 기존 member를 남기지 않는다.")
    @Test
    void atomicallyReplacesCanonicalSnapshot() {
        LocalDate date = LocalDate.of(2026, 7, 17);
        String key = DailyRankingKey.from(date);
        redisTemplate.opsForZSet().add(key, "old", 99.0);

        long count = publisher.publish(date, 1L, List.of(
            new DailyRankingScore(1L, 3.4),
            new DailyRankingScore(2L, 1.2)
        ));

        assertThat(count).isEqualTo(2);
        assertThat(redisTemplate.opsForZSet().score(key, "old")).isNull();
        assertThat(redisTemplate.opsForZSet().score(key, "1")).isEqualTo(3.4);
        assertThat(redisTemplate.getExpire(key)).isBetween(172_790L, 172_800L);
    }

    @DisplayName("같은 날짜를 다시 발행하면 증분 누적 없이 새 절대 점수와 정확히 일치한다.")
    @Test
    void rerunIsIdempotent() {
        LocalDate date = LocalDate.of(2026, 7, 17);
        String key = DailyRankingKey.from(date);
        List<DailyRankingScore> scores = List.of(new DailyRankingScore(1L, 3.4));

        publisher.publish(date, 1L, scores);
        publisher.publish(date, 2L, scores);

        assertThat(redisTemplate.opsForZSet().size(key)).isEqualTo(1);
        assertThat(redisTemplate.opsForZSet().score(key, "1")).isEqualTo(3.4);
    }

    @DisplayName("임시 ZSET 구성 중 실패하면 기존 canonical 랭킹을 유지한다.")
    @Test
    void preservesCanonicalSnapshotWhenBuildFails() {
        LocalDate date = LocalDate.of(2026, 7, 17);
        String key = DailyRankingKey.from(date);
        String temporaryKey = key + ":building:3";
        redisTemplate.opsForZSet().add(key, "old", 99.0);
        var delegate = new ArrayList<DailyRankingScore>();
        for (long productId = 1; productId <= 1_000; productId++) {
            delegate.add(new DailyRankingScore(productId, 1.0));
        }
        delegate.add(new DailyRankingScore(null, 1.0));
        List<DailyRankingScore> scores = new AbstractList<>() {
            @Override
            public DailyRankingScore get(int index) {
                return delegate.get(index);
            }

            @Override
            public int size() {
                return delegate.size();
            }

            @Override
            public List<DailyRankingScore> subList(int fromIndex, int toIndex) {
                if (fromIndex == 500) {
                    redisTemplate.expire(temporaryKey, Duration.ofSeconds(30));
                }
                return delegate.subList(fromIndex, toIndex);
            }
        };

        assertThatThrownBy(() -> publisher.publish(date, 3L, scores))
            .isInstanceOf(NullPointerException.class);

        assertThat(redisTemplate.opsForZSet().size(key)).isEqualTo(1);
        assertThat(redisTemplate.opsForZSet().score(key, "old")).isEqualTo(99.0);
        assertThat(redisTemplate.opsForZSet().score(temporaryKey, "__snapshot_marker__")).isEqualTo(0.0);
        assertThat(redisTemplate.getExpire(temporaryKey)).isBetween(3_590L, 3_600L);
    }

    @DisplayName("빈 스냅샷도 기존 canonical 랭킹을 빈 결과로 교체한다.")
    @Test
    void publishesEmptySnapshot() {
        LocalDate date = LocalDate.of(2026, 7, 17);
        String key = DailyRankingKey.from(date);
        redisTemplate.opsForZSet().add(key, "old", 99.0);

        long count = publisher.publish(date, 4L, List.of());

        assertThat(count).isZero();
        assertThat(redisTemplate.opsForZSet().size(key)).isZero();
    }

    @DisplayName("임시 키가 만료되어도 기존 canonical 랭킹을 삭제하지 않는다.")
    @Test
    @SuppressWarnings("unchecked")
    void preservesCanonicalSnapshotWhenTemporaryKeyIsMissing() {
        LocalDate date = LocalDate.of(2026, 7, 17);
        String canonicalKey = DailyRankingKey.from(date);
        String missingTemporaryKey = canonicalKey + ":building:missing";
        redisTemplate.opsForZSet().add(canonicalKey, "old", 99.0);
        DefaultRedisScript<Long> script = (DefaultRedisScript<Long>) ReflectionTestUtils.getField(
            DailyRankingSnapshotPublisher.class,
            "PUBLISH_SCRIPT"
        );

        Long result = redisTemplate.execute(
            script,
            List.of(missingTemporaryKey, canonicalKey),
            "__snapshot_marker__",
            "172800"
        );

        assertThat(result).isEqualTo(-1L);
        assertThat(redisTemplate.opsForZSet().size(canonicalKey)).isEqualTo(1);
        assertThat(redisTemplate.opsForZSet().score(canonicalKey, "old")).isEqualTo(99.0);
    }

    @DisplayName("임시 키가 일부 member만 갖고 marker를 잃으면 canonical로 발행하지 않는다.")
    @Test
    @SuppressWarnings("unchecked")
    void preservesCanonicalSnapshotWhenTemporaryMarkerIsMissing() {
        LocalDate date = LocalDate.of(2026, 7, 17);
        String canonicalKey = DailyRankingKey.from(date);
        String partialTemporaryKey = canonicalKey + ":building:partial";
        redisTemplate.opsForZSet().add(canonicalKey, "old", 99.0);
        redisTemplate.opsForZSet().add(partialTemporaryKey, "1", 1.0);
        DefaultRedisScript<Long> script = (DefaultRedisScript<Long>) ReflectionTestUtils.getField(
            DailyRankingSnapshotPublisher.class,
            "PUBLISH_SCRIPT"
        );

        Long result = redisTemplate.execute(
            script,
            List.of(partialTemporaryKey, canonicalKey),
            "__snapshot_marker__",
            "172800"
        );

        assertThat(result).isEqualTo(-1L);
        assertThat(redisTemplate.opsForZSet().score(canonicalKey, "old")).isEqualTo(99.0);
        assertThat(redisTemplate.opsForZSet().score(partialTemporaryKey, "1")).isEqualTo(1.0);
    }
}
