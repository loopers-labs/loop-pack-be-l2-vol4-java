package com.loopers.domain.ranking;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public final class RankingCarryOverKeyPolicy {

  private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("uuuuMMdd");
  private static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("uuuuMMddHH");

  private RankingCarryOverKeyPolicy() {}

  public static RankingCarryOverKeys keys(Instant executionTime) {
    Objects.requireNonNull(executionTime, "executionTime은 필수입니다.");
    ZonedDateTime seoulTime = executionTime.atZone(SEOUL_ZONE);
    String sourceHour = HOUR_FORMATTER.format(seoulTime);
    String targetDate = DATE_FORMATTER.format(seoulTime.plusDays(1));
    return new RankingCarryOverKeys(
        "ranking:hour:" + sourceHour,
        "ranking:all:" + targetDate,
        "ranking:carry-over:" + targetDate);
  }
}
