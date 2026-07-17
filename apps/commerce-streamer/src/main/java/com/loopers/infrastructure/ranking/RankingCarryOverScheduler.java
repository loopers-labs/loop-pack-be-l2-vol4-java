package com.loopers.infrastructure.ranking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 자정에 랭킹판이 텅 비는 콜드스타트를 완화하기 위해,
 * 매일 23:50에 오늘 상위 랭킹을 감쇠시켜 내일 랭킹에 미리 심어둔다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class RankingCarryOverScheduler {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final int CARRY_OVER_TOP_N = 100;
    private static final double CARRY_OVER_DECAY_FACTOR = 0.1;

    private final RankingRedisRepository rankingRedisRepository;

    @Scheduled(cron = "0 50 23 * * *", zone = "Asia/Seoul")
    public void carryOverScoresToNextDay() {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate tomorrow = today.plusDays(1);

        rankingRedisRepository.carryOverTopScores(today, tomorrow, CARRY_OVER_TOP_N, CARRY_OVER_DECAY_FACTOR);

        log.info("[RankingCarryOver] {} -> {} 상위 {}개 점수를 {}배로 이월했습니다.",
            today, tomorrow, CARRY_OVER_TOP_N, CARRY_OVER_DECAY_FACTOR);
    }
}
