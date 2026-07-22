package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingCarryOverResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RankingCarryOverScheduler {

  static final String SEOUL_ZONE_ID = "Asia/Seoul";

  private final RankingCarryOverService carryOverService;
  private final Clock clock;

  @Autowired
  public RankingCarryOverScheduler(RankingCarryOverService carryOverService) {
    this(carryOverService, Clock.system(ZoneId.of(SEOUL_ZONE_ID)));
  }

  RankingCarryOverScheduler(RankingCarryOverService carryOverService, Clock clock) {
    this.carryOverService = carryOverService;
    this.clock = clock;
  }

  @Scheduled(cron = "0 50 23 * * *", zone = SEOUL_ZONE_ID)
  public void carryOverAt2350() {
    Instant executionTime = clock.instant();
    try {
      RankingCarryOverResult result = carryOverService.carryOver(executionTime);
      logResult(result, executionTime);
    } catch (RuntimeException exception) {
      log.warn("[ranking] 시간 랭킹 이월에 실패해 이번 실행을 생략합니다. executionTime={}", executionTime, exception);
    }
  }

  private void logResult(RankingCarryOverResult result, Instant executionTime) {
    switch (result.status()) {
      case COMPLETED ->
          log.info(
              "[ranking] 다음 날 랭킹 이월을 완료했습니다. executionTime={}, productCount={}",
              executionTime,
              result.carriedProductCount());
      case SOURCE_NOT_FOUND ->
          log.warn("[ranking] 원본 시간 랭킹이 없어 이월을 생략합니다. executionTime={}", executionTime);
      case ALREADY_COMPLETED ->
          log.info("[ranking] 이미 완료된 랭킹 이월 실행입니다. executionTime={}", executionTime);
    }
  }
}
