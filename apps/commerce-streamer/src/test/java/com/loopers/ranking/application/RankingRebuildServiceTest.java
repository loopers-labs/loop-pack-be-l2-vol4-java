package com.loopers.ranking.application;

import com.loopers.ranking.domain.RankingSnapshot;
import com.loopers.ranking.infrastructure.RankingSnapshotJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
class RankingRebuildServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final long TWO_DAYS_SECONDS = 2 * 24 * 60 * 60L;

    @Autowired
    private RankingRebuildService rankingRebuildService;
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    @Autowired
    private RankingSnapshotJpaRepository snapshotRepository;
    @Autowired
    private RedisCleanUp redisCleanUp;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
        databaseCleanUp.truncateAllTables();
    }

    private String todayKey() {
        return "ranking:all:" + LocalDate.now(SEOUL).format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    @DisplayName("어제 스냅샷 × 0.1 로 오늘 판을 재구축하고 TTL 을 건다")
    @Test
    void rebuildToday_seedsFromYesterdaySnapshot() {
        LocalDate yesterday = LocalDate.now(SEOUL).minusDays(1);
        snapshotRepository.save(new RankingSnapshot(yesterday, 101L, 1, 100.0));
        snapshotRepository.save(new RankingSnapshot(yesterday, 202L, 2, 50.0));

        int count = rankingRebuildService.rebuildToday();

        assertThat(count).isEqualTo(2);
        assertThat(redisTemplate.opsForZSet().score(todayKey(), "101")).isCloseTo(10.0, within(1e-9));
        assertThat(redisTemplate.opsForZSet().score(todayKey(), "202")).isCloseTo(5.0, within(1e-9));
        Long ttl = redisTemplate.getExpire(todayKey(), TimeUnit.SECONDS);
        assertThat(ttl).isNotNull().isBetween(1L, TWO_DAYS_SECONDS);
    }

    @DisplayName("재구축은 기존 오늘 키를 원자적으로 교체한다(temp + RENAME)")
    @Test
    void rebuildToday_atomicallyReplacesExisting() {
        LocalDate yesterday = LocalDate.now(SEOUL).minusDays(1);
        redisTemplate.opsForZSet().add(todayKey(), "999", 999.0); // 크래시 후 남은 stale
        snapshotRepository.save(new RankingSnapshot(yesterday, 101L, 1, 100.0));

        rankingRebuildService.rebuildToday();

        assertThat(redisTemplate.opsForZSet().score(todayKey(), "999")).isNull();
        assertThat(redisTemplate.opsForZSet().score(todayKey(), "101")).isCloseTo(10.0, within(1e-9));
    }
}
