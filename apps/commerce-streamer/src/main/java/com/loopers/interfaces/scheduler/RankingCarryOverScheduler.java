package com.loopers.interfaces.scheduler;

import com.loopers.infrastructure.ranking.RankingRedisStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 랭킹 콜드스타트 이월 스케줄러.
 *
 * <p>매일 23:50 에 다음 날 일간 랭킹 키를 오늘 점수의 10% 로 미리 시딩한다 — 자정 직후 랭킹이 텅 비어
 * "0점부터 경쟁"하는 콜드스타트를 완화한다. 시간별 키(ranking:hour)는 의도적으로 이월하지 않는다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class RankingCarryOverScheduler {

    private final RankingRedisStore rankingRedisStore;

    @Scheduled(cron = "0 50 23 * * *")
    public void carryOverToTomorrow() {
        LocalDate today = LocalDate.now();
        rankingRedisStore.carryOver(today, today.plusDays(1));
        log.info("[Ranking] 콜드스타트 이월 실행 — {} → {}", today, today.plusDays(1));
    }
}
