package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingKeys;
import com.loopers.domain.ranking.RankingProperties;
import com.loopers.domain.ranking.RankingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 콜드 스타트 완화 — 23:50(KST)에 내일 랭킹판을 오늘 점수 × carryOverRate 로 미리 생성한다.
 * 자정 직후에도 "어제 인기 상품"이 랭킹에 남는다. 23:50~24:00 사이 이벤트는 carry-over 에서
 * 빠지지만(근사 허용) 오늘 키에는 정상 반영된다.
 * 테스트 프로필에선 미등록(@Profile("!test")) — 통합테스트의 ZSET 을 건드리지 않는다.
 */
@Slf4j
@Profile("!test")
@RequiredArgsConstructor
@Component
public class RankingCarryOverScheduler {

    private final RankingRepository rankingRepository;
    private final RankingProperties properties;

    @Scheduled(cron = "0 50 23 * * *", zone = "Asia/Seoul")
    public void preSeedTomorrow() {
        LocalDate today = LocalDate.now(RankingKeys.ZONE);
        rankingRepository.carryOver(today, today.plusDays(1), properties.carryOverRate());
        log.info("랭킹 carry-over 완료 — {} → {} (rate={})", today, today.plusDays(1), properties.carryOverRate());
    }
}
