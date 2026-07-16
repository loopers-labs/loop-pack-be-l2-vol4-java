package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingService;
import com.loopers.infrastructure.ranking.RankingCarryOverProperties;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

// 매일 23시 50분에 오늘 점수의 일부(carryOverRatio)를 내일 키에 미리 반영해 콜드 스타트를 완화한다.
// (@EnableScheduling 은 CommerceStreamerApplication 에 이미 선언돼 있다.)
// ranking.carry-over-scheduler-enabled=false(test 프로필)면 자동 tick 은 꺼지지만 carryOver() 자체는 테스트가 직접 호출할 수 있다.
@Slf4j
@Component
public class RankingCarryOverScheduler {

    private final RankingService rankingService;
    private final RankingCarryOverProperties properties;
    private final AtomicLong lastExecutionTimestamp = new AtomicLong();

    public RankingCarryOverScheduler(
            RankingService rankingService,
            RankingCarryOverProperties properties,
            MeterRegistry meterRegistry
    ) {
        this.rankingService = rankingService;
        this.properties = properties;
        meterRegistry.gauge("ranking.carry-over.scheduler.last.execution.timestamp", lastExecutionTimestamp);
    }

    @Scheduled(cron = "0 50 23 * * *")
    public void scheduledCarryOver() {
        if (properties.carryOverSchedulerEnabled()) {
            carryOver();
        }
    }

    public void carryOver() {
        try {
            LocalDate today = LocalDate.now();
            rankingService.carryOverScores(today, today.plusDays(1), properties.carryOverRatio());
        } catch (Exception e) {
            log.error("랭킹 Score Carry-Over 실패", e);
        } finally {
            lastExecutionTimestamp.set(Instant.now().toEpochMilli());
        }
    }
}
