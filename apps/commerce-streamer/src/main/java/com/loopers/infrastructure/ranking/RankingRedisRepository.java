package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingKeys;
import com.loopers.domain.ranking.RankingRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class RankingRedisRepository implements RankingRepository {

  public static final long RANKING_TTL_SECONDS = 172_800L;

  private static final DefaultRedisScript<Long> INCREMENT_IF_UNPROCESSED_SCRIPT =
      new DefaultRedisScript<>(
          """
          local added = redis.call('SADD', KEYS[3], ARGV[1])
          if added == 0 then
              return 0
          end
          redis.call('ZINCRBY', KEYS[1], ARGV[2], ARGV[3])
          redis.call('ZINCRBY', KEYS[2], ARGV[2], ARGV[3])
          redis.call('EXPIRE', KEYS[1], ARGV[4])
          redis.call('EXPIRE', KEYS[2], ARGV[4])
          redis.call('EXPIRE', KEYS[3], ARGV[4])
          return 1
          """,
          Long.class);

  private final RedisTemplate<String, String> redisTemplate;

  public RankingRedisRepository(
      @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  @Override
  public boolean incrementIfUnprocessed(
      RankingKeys keys, UUID eventId, Long productId, double score) {
    Long result =
        redisTemplate.execute(
            INCREMENT_IF_UNPROCESSED_SCRIPT,
            List.of(keys.rankingKey(), keys.hourlyRankingKey(), keys.processedEventKey()),
            eventId.toString(),
            Double.toString(score),
            productId.toString(),
            Long.toString(RANKING_TTL_SECONDS));
    if (result == null) {
      throw new IllegalStateException("Redis 랭킹 점수 반영 결과가 없습니다.");
    }
    return result == 1L;
  }
}
