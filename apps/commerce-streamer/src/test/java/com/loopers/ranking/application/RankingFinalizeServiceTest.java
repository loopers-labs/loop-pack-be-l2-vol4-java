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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
class RankingFinalizeServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Autowired
    private RankingFinalizeService rankingFinalizeService;
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

    private String keyOf(LocalDate date) {
        return "ranking:all:" + date.format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    @DisplayName("어제 ZSET 을 순위(1..N)와 함께 스냅샷으로 확정한다")
    @Test
    void finalizeYesterday_persistsRankedSnapshot() {
        LocalDate yesterday = LocalDate.now(SEOUL).minusDays(1);
        redisTemplate.opsForZSet().add(keyOf(yesterday), "101", 100.0);
        redisTemplate.opsForZSet().add(keyOf(yesterday), "202", 50.0);
        redisTemplate.opsForZSet().add(keyOf(yesterday), "303", 10.0);

        rankingFinalizeService.finalizeYesterday();

        List<RankingSnapshot> rows = snapshotRepository.findByStatDateOrderByRankPositionAsc(yesterday);
        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(RankingSnapshot::getProductId).containsExactly(101L, 202L, 303L);
        assertThat(rows).extracting(RankingSnapshot::getRankPosition).containsExactly(1, 2, 3);
        assertThat(rows.get(0).getScore()).isCloseTo(100.0, within(1e-9));
    }

    @DisplayName("재실행하면 기존 스냅샷을 교체한다(멱등)")
    @Test
    void finalizeYesterday_isIdempotent() {
        LocalDate yesterday = LocalDate.now(SEOUL).minusDays(1);
        redisTemplate.opsForZSet().add(keyOf(yesterday), "101", 100.0);

        rankingFinalizeService.finalizeYesterday();
        rankingFinalizeService.finalizeYesterday();

        List<RankingSnapshot> rows = snapshotRepository.findByStatDateOrderByRankPositionAsc(yesterday);
        assertThat(rows).hasSize(1);
    }
}
