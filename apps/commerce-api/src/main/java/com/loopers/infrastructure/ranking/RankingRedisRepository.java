package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingPage;
import com.loopers.domain.ranking.RankingRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Component;

@Component
public class RankingRedisRepository implements RankingRepository {

  private static final DateTimeFormatter KEY_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
  private static final DateTimeFormatter KEY_HOUR_FORMATTER =
      DateTimeFormatter.ofPattern("uuuuMMddHH");
  private static final String DAILY_KEY_PREFIX = "ranking:all:";
  private static final String HOURLY_KEY_PREFIX = "ranking:hour:";

  private final RedisTemplate<String, String> masterRedisTemplate;

  public RankingRedisRepository(
      @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
          RedisTemplate<String, String> masterRedisTemplate) {
    this.masterRedisTemplate = masterRedisTemplate;
  }

  @Override
  public RankingPage findPage(LocalDate date, int page, int size) {
    return findPageByKey(dailyKey(date), page, size);
  }

  @Override
  public RankingPage findHourlyPage(LocalDateTime dateTime, int page, int size) {
    return findPageByKey(hourlyKey(dateTime), page, size);
  }

  private RankingPage findPageByKey(String key, int page, int size) {
    long offset = (long) (page - 1) * size;
    long end = offset + size - 1L;

    Long count = masterRedisTemplate.opsForZSet().zCard(key);
    Set<TypedTuple<String>> tuples =
        masterRedisTemplate.opsForZSet().reverseRangeWithScores(key, offset, end);

    List<RankingEntry> entries = new ArrayList<>();
    if (tuples != null) {
      long rank = offset + 1L;
      for (TypedTuple<String> tuple : tuples) {
        String member = tuple.getValue();
        Double score = tuple.getScore();
        if (member != null && score != null) {
          entries.add(new RankingEntry(Long.valueOf(member), rank, score));
        }
        rank++;
      }
    }

    return new RankingPage(List.copyOf(entries), count == null ? 0L : count);
  }

  @Override
  public OptionalLong findRank(LocalDate date, Long productId) {
    Long zeroBasedRank =
        masterRedisTemplate.opsForZSet().reverseRank(dailyKey(date), productId.toString());
    return zeroBasedRank == null ? OptionalLong.empty() : OptionalLong.of(zeroBasedRank + 1L);
  }

  public static String dailyKey(LocalDate date) {
    return DAILY_KEY_PREFIX + KEY_DATE_FORMATTER.format(date);
  }

  public static String hourlyKey(LocalDateTime dateTime) {
    return HOURLY_KEY_PREFIX + KEY_HOUR_FORMATTER.format(dateTime);
  }
}
