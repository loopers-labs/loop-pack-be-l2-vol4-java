package com.loopers.infrastructure.ranking;

import com.loopers.application.ranking.RankingScoreComposer;
import com.loopers.domain.ranking.RankingKeys;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.support.config.RankingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 23:50 carry-over — 당일 raw 점수 × 계수를 익일 raw 보드에 미리 시드하고, 즉시 익일 display 를 합성한다.
 *
 * <p>자정에 조회 키가 익일로 전환되는 순간 보드가 이미 존재하므로 콜드 스타트 창이 없다(전환 경계와
 * 시드 시점의 레이스 제거). 비용은 시드 스냅샷이 23:50 시점이라 마지막 10분 활동이 이월에서 빠지는 것 —
 * 계수 0.1 이 걸린 시드에서는 근사 예산 안이다. 버킷 귀속이 occurredAt 이라 23:50~자정 사이 이벤트는
 * 여전히 당일 보드로 가므로 익일 시드와 충돌하지 않는다.</p>
 *
 * <p>멱등·다중 인스턴스 가드는 저장소의 SETNX 마커가 책임진다(재실행·동시 실행 시 skip). 23:50 에 앱이
 * 죽어 있으면 그날 시드는 유실되지만 콜드 스타트 품질 저하일 뿐 정합성 사고가 아니므로 보정 잡은 두지 않는다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ranking.carry-over.enabled", havingValue = "true")
public class RankingCarryOverScheduler {

    private final RankingRepository rankingRepository;
    private final RankingScoreComposer rankingScoreComposer;
    private final RankingProperties rankingProperties;

    @Scheduled(cron = "0 50 23 * * *", zone = "Asia/Seoul")
    public void carryOver() {
        LocalDate tomorrow = LocalDate.now(RankingKeys.ZONE).plusDays(1);
        try {
            boolean executed = rankingRepository.carryOver(
                    tomorrow.minusDays(1), tomorrow, rankingProperties.carryOver().weight());
            rankingScoreComposer.compose(tomorrow);
            log.info("랭킹 carry-over (date={}) — {}", tomorrow, executed ? "시드 + 즉시 합성 완료" : "이미 수행됨, 합성만 갱신");
        } catch (Exception e) {
            log.error("랭킹 carry-over 실패 (date={})", tomorrow, e);
        }
    }
}
