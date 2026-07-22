package com.loopers.infrastructure.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingKeys;
import com.loopers.utils.RedisCleanUp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

@SpringBootTest
class RankingRedisRepositoryIntegrationTest {

  private final RankingRedisRepository rankingRepository;
  private final RedisTemplate<String, String> masterRedisTemplate;
  private final RedisCleanUp redisCleanUp;

  @Autowired
  RankingRedisRepositoryIntegrationTest(
      RankingRedisRepository rankingRepository,
      @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
          RedisTemplate<String, String> masterRedisTemplate,
      RedisCleanUp redisCleanUp) {
    this.rankingRepository = rankingRepository;
    this.masterRedisTemplate = masterRedisTemplate;
    this.redisCleanUp = redisCleanUp;
  }

  @AfterEach
  void tearDown() {
    redisCleanUp.truncateAll();
  }

  @DisplayName("동일 eventId는 일간·시간 랭킹에 한 번만 반영하고 세 키에 48시간 TTL을 설정한다.")
  @Test
  void incrementsOnceAndSetsTtl() {
    RankingKeys keys =
        new RankingKeys(
            "ranking:all:20260716", "ranking:hour:2026071614", "ranking:processed:20260716");
    UUID eventId = UUID.randomUUID();

    boolean first = rankingRepository.incrementIfUnprocessed(keys, eventId, 1L, 0.2D);
    boolean duplicate = rankingRepository.incrementIfUnprocessed(keys, eventId, 1L, 0.2D);

    assertThat(first).isTrue();
    assertThat(duplicate).isFalse();
    assertThat(masterRedisTemplate.opsForZSet().score(keys.rankingKey(), "1")).isEqualTo(0.2D);
    assertThat(masterRedisTemplate.opsForZSet().score(keys.hourlyRankingKey(), "1"))
        .isEqualTo(0.2D);
    assertThat(masterRedisTemplate.getExpire(keys.rankingKey()))
        .isBetween(1L, RankingRedisRepository.RANKING_TTL_SECONDS);
    assertThat(masterRedisTemplate.getExpire(keys.hourlyRankingKey()))
        .isBetween(1L, RankingRedisRepository.RANKING_TTL_SECONDS);
    assertThat(masterRedisTemplate.getExpire(keys.processedEventKey()))
        .isBetween(1L, RankingRedisRepository.RANKING_TTL_SECONDS);
  }

  @DisplayName("조회 2건, 신규 좋아요 1건, 완료 주문 1건의 점수는 1.4로 누적된다.")
  @Test
  void accumulatesUniqueEvents() {
    RankingKeys keys =
        new RankingKeys(
            "ranking:all:20260716", "ranking:hour:2026071614", "ranking:processed:20260716");

    rankingRepository.incrementIfUnprocessed(keys, UUID.randomUUID(), 1L, 0.1D);
    rankingRepository.incrementIfUnprocessed(keys, UUID.randomUUID(), 1L, 0.1D);
    rankingRepository.incrementIfUnprocessed(keys, UUID.randomUUID(), 1L, 0.2D);
    rankingRepository.incrementIfUnprocessed(keys, UUID.randomUUID(), 1L, 1.0D);

    assertThat(masterRedisTemplate.opsForZSet().score(keys.rankingKey(), "1"))
        .isCloseTo(1.4D, org.assertj.core.data.Offset.offset(0.000_001D));
    assertThat(masterRedisTemplate.opsForZSet().score(keys.hourlyRankingKey(), "1"))
        .isCloseTo(1.4D, org.assertj.core.data.Offset.offset(0.000_001D));
  }

  @DisplayName("서로 다른 이벤트가 동시에 도착해도 일간·시간 점수를 손실 없이 누적한다.")
  @Test
  void accumulatesConcurrentUniqueEvents() throws Exception {
    RankingKeys keys =
        new RankingKeys(
            "ranking:all:20260716", "ranking:hour:2026071614", "ranking:processed:20260716");
    int eventCount = 50;
    CountDownLatch start = new CountDownLatch(1);

    try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
      List<Future<Boolean>> results = new ArrayList<>();
      for (int index = 0; index < eventCount; index++) {
        UUID eventId = UUID.randomUUID();
        results.add(
            executor.submit(
                () -> {
                  start.await();
                  return rankingRepository.incrementIfUnprocessed(keys, eventId, 1L, 0.1D);
                }));
      }

      start.countDown();
      for (Future<Boolean> result : results) {
        assertThat(result.get(5, TimeUnit.SECONDS)).isTrue();
      }
    }

    assertThat(masterRedisTemplate.opsForZSet().score(keys.rankingKey(), "1"))
        .isCloseTo(5.0D, org.assertj.core.data.Offset.offset(0.000_001D));
    assertThat(masterRedisTemplate.opsForZSet().score(keys.hourlyRankingKey(), "1"))
        .isCloseTo(5.0D, org.assertj.core.data.Offset.offset(0.000_001D));
  }
}
