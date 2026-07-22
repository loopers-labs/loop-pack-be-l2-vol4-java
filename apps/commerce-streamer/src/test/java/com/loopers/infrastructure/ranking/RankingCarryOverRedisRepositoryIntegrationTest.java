package com.loopers.infrastructure.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import com.loopers.application.ranking.RankingCarryOverService;
import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingCarryOverKeys;
import com.loopers.domain.ranking.RankingCarryOverResult;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

@SpringBootTest
class RankingCarryOverRedisRepositoryIntegrationTest {

  private final RankingCarryOverRedisRepository repository;
  private final RedisTemplate<String, String> redisTemplate;
  private final RedisCleanUp redisCleanUp;

  @Autowired
  RankingCarryOverRedisRepositoryIntegrationTest(
      RankingCarryOverRedisRepository repository,
      @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate,
      RedisCleanUp redisCleanUp) {
    this.repository = repository;
    this.redisTemplate = redisTemplate;
    this.redisCleanUp = redisCleanUp;
  }

  @AfterEach
  void tearDown() {
    redisCleanUp.truncateAll();
  }

  @DisplayName("시간 랭킹의 10%를 다음 날 일간 랭킹에 한 번만 누적한다.")
  @Test
  void carriesScoresOnlyOnce() {
    RankingCarryOverKeys keys =
        new RankingCarryOverKeys(
            "ranking:hour:2026071623", "ranking:all:20260717", "ranking:carry-over:20260717");
    redisTemplate.opsForZSet().add(keys.sourceHourlyKey(), "1", 10.0D);
    redisTemplate.opsForZSet().add(keys.sourceHourlyKey(), "2", 3.0D);
    redisTemplate.opsForZSet().add(keys.targetDailyKey(), "1", 1.0D);

    RankingCarryOverResult first =
        repository.carryOver(
            keys,
            RankingCarryOverService.CARRY_OVER_RATIO,
            RankingCarryOverService.CARRY_OVER_TTL_SECONDS);
    RankingCarryOverResult duplicate =
        repository.carryOver(
            keys,
            RankingCarryOverService.CARRY_OVER_RATIO,
            RankingCarryOverService.CARRY_OVER_TTL_SECONDS);

    assertThat(first.status()).isEqualTo(RankingCarryOverResult.Status.COMPLETED);
    assertThat(first.carriedProductCount()).isEqualTo(2L);
    assertThat(duplicate.status()).isEqualTo(RankingCarryOverResult.Status.ALREADY_COMPLETED);
    assertThat(redisTemplate.opsForZSet().score(keys.targetDailyKey(), "1")).isEqualTo(2.0D);
    assertThat(redisTemplate.opsForZSet().score(keys.targetDailyKey(), "2"))
        .isCloseTo(0.3D, org.assertj.core.data.Offset.offset(0.000_001D));
    assertThat(redisTemplate.getExpire(keys.targetDailyKey()))
        .isBetween(1L, RankingCarryOverService.CARRY_OVER_TTL_SECONDS);
    assertThat(redisTemplate.getExpire(keys.markerKey()))
        .isBetween(1L, RankingCarryOverService.CARRY_OVER_TTL_SECONDS);
  }

  @DisplayName("원본 시간 랭킹이 없으면 마커와 다음 날 랭킹을 만들지 않는다.")
  @Test
  void skipsWhenSourceDoesNotExist() {
    RankingCarryOverKeys keys =
        new RankingCarryOverKeys(
            "ranking:hour:2026071623", "ranking:all:20260717", "ranking:carry-over:20260717");

    RankingCarryOverResult result =
        repository.carryOver(
            keys,
            RankingCarryOverService.CARRY_OVER_RATIO,
            RankingCarryOverService.CARRY_OVER_TTL_SECONDS);

    assertThat(result.status()).isEqualTo(RankingCarryOverResult.Status.SOURCE_NOT_FOUND);
    assertThat(redisTemplate.hasKey(keys.targetDailyKey())).isFalse();
    assertThat(redisTemplate.hasKey(keys.markerKey())).isFalse();
  }
}
