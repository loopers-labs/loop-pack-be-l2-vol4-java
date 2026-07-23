package com.loopers.infrastructure.ranking;

import com.loopers.application.ranking.RankingScoreComposer;
import com.loopers.domain.ranking.RankingKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Score 구간 — display 보드를 주기적으로 재합성한다. 서빙 신선도 = 합성 주기.
 *
 * <p>합성은 destination 덮어쓰기 재계산이라 멱등이며 다중 인스턴스 동시 실행도 무해(분산 락 불요).
 * 가중치(properties)를 바꾸면 다음 합성부터 당일 전체에 소급된다.</p>
 *
 * <p>전일도 함께 재합성한다 — 자정 이후 도착한 전일 occurredAt 이벤트가 전일 raw 에 반영되므로,
 * 전일 display 도 갱신해 보드가 raw 와 어긋난 채 남지 않게 한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ranking.compose.enabled", havingValue = "true")
public class RankingComposeScheduler {

    private final RankingScoreComposer rankingScoreComposer;

    @Scheduled(fixedDelayString = "${ranking.compose.interval-ms}")
    public void compose() {
        LocalDate today = LocalDate.now(RankingKeys.ZONE);
        try {
            rankingScoreComposer.compose(today);
            rankingScoreComposer.compose(today.minusDays(1));
        } catch (Exception e) {
            // 스케줄러 스레드 보호 — 실패해도 다음 주기가 전체 재계산하므로 복구 행위가 따로 없다.
            log.error("랭킹 display 합성 실패 (date={})", today, e);
        }
    }
}
