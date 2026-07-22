package com.loopers.application.ranking;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RankingCarryOverSchedulerTest {

  @DisplayName("Redis 이월 실패는 스케줄러 밖으로 전파하지 않는다.")
  @Test
  void skipsCurrentRunWhenCarryOverFails() {
    RankingCarryOverService service = mock(RankingCarryOverService.class);
    Instant executionTime = Instant.parse("2026-07-16T14:50:00Z");
    Clock clock = Clock.fixed(executionTime, ZoneId.of("Asia/Seoul"));
    RankingCarryOverScheduler scheduler = new RankingCarryOverScheduler(service, clock);
    when(service.carryOver(executionTime)).thenThrow(new IllegalStateException("redis down"));

    assertThatCode(scheduler::carryOverAt2350).doesNotThrowAnyException();
    verify(service).carryOver(executionTime);
  }
}
