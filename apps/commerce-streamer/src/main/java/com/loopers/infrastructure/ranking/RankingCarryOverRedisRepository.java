package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingCarryOverKeys;
import com.loopers.domain.ranking.RankingCarryOverRepository;
import com.loopers.domain.ranking.RankingCarryOverResult;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class RankingCarryOverRedisRepository implements RankingCarryOverRepository {

  private static final long SOURCE_NOT_FOUND = -1L;
  private static final long ALREADY_COMPLETED = -2L;

  private static final DefaultRedisScript<Long> CARRY_OVER_SCRIPT =
      new DefaultRedisScript<>(
          """
          if redis.call('EXISTS', KEYS[1]) == 0 then
              return -1
          end
          local claimed = redis.call('SET', KEYS[3], ARGV[1], 'NX', 'EX', ARGV[3])
          if not claimed then
              return -2
          end
          local entries = redis.call('ZRANGE', KEYS[1], 0, -1, 'WITHSCORES')
          local count = 0
          for index = 1, #entries, 2 do
              local carriedScore = tonumber(entries[index + 1]) * tonumber(ARGV[2])
              if carriedScore ~= 0 then
                  redis.call('ZINCRBY', KEYS[2], tostring(carriedScore), entries[index])
                  count = count + 1
              end
          end
          if count > 0 then
              redis.call('EXPIRE', KEYS[2], ARGV[3])
          end
          return count
          """,
          Long.class);

  private final RedisTemplate<String, String> redisTemplate;

  public RankingCarryOverRedisRepository(
      @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  @Override
  public RankingCarryOverResult carryOver(
      RankingCarryOverKeys keys, double carryOverRatio, long ttlSeconds) {
    Long result =
        redisTemplate.execute(
            CARRY_OVER_SCRIPT,
            List.of(keys.sourceHourlyKey(), keys.targetDailyKey(), keys.markerKey()),
            "completed",
            Double.toString(carryOverRatio),
            Long.toString(ttlSeconds));
    if (result == null) {
      throw new IllegalStateException("Redis 랭킹 이월 결과가 없습니다.");
    }
    if (result == SOURCE_NOT_FOUND) {
      return RankingCarryOverResult.sourceNotFound();
    }
    if (result == ALREADY_COMPLETED) {
      return RankingCarryOverResult.alreadyCompleted();
    }
    return RankingCarryOverResult.completed(result);
  }
}
