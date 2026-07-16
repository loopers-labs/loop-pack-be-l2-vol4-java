package com.loopers.infrastructure.ranking;

import com.loopers.config.RankingProperties;
import com.loopers.domain.ranking.ProductRanking;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 매일 23:50 에 오늘 랭킹 점수의 일부(carry-over weight)를 다음 날 키로 미리 복사해 콜드 스타트를 완화한다.
 * 일별 키 리셋으로 일자 변경 순간 랭킹이 비는 문제를 막되, weight 를 작게(0.1) 잡아 롱테일 부활을 방지한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ranking.carry-over", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class RankingCarryOverScheduler {

    private final ProductRanking productRanking;
    private final RankingProperties rankingProperties;

    @Scheduled(cron = "0 50 23 * * *")
    public void carryOver() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        double weight = rankingProperties.carryOver().weight();
        productRanking.carryOver(today, tomorrow, weight);
        log.info("랭킹 carry-over 실행 - {} → {} (weight={})", today, tomorrow, weight);
    }
}