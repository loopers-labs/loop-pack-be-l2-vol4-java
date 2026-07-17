package com.loopers.domain.ranking;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public final class RankingKeyPolicy {

  private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
  private static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("uuuuMMddHH");

  private RankingKeyPolicy() {}

  public static RankingKeys dailyKeys(Instant occurredAt) {
    Objects.requireNonNull(occurredAt, "occurredAt은 필수입니다.");
    var seoulDateTime = occurredAt.atZone(SEOUL_ZONE);
    String date = DATE_FORMATTER.format(seoulDateTime.toLocalDate());
    String hour = HOUR_FORMATTER.format(seoulDateTime);
    return new RankingKeys(
        "ranking:all:" + date, "ranking:hour:" + hour, "ranking:processed:" + date);
  }
}
