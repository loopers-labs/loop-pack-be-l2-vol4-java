package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 콜드 스타트 완화 — 매일 23:50에 오늘 랭킹 상위 항목을 감쇠 계수를 곱해 내일 키로 이월한다.
 * 자정 직후 내일 랭킹판이 빈 상태로 노출되는 것을 막고, 전날의 인기가 절반의 가중치로 이어지게 한다.
 * <p>
 * 쓰기는 ZADD NX(saveScoreIfAbsent) — 이월 시점(23:50~자정)의 내일 키는 비어 있어 전부 기록되고,
 * 다중 인스턴스·재실행으로 스케줄러가 겹쳐도 이미 기록된 항목은 덮어쓰지 않아 안전하다.
 * 자정 이후 실제 이벤트는 ZINCRBY로 이월 점수 위에 누적된다.
 */
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ranking.carry-over.enabled", matchIfMissing = true)
@Component
public class RankingCarryOverScheduler {

    /**
     * 감쇠 계수 0.3 — 이월 계수 1.0이어야 전날:오늘이 반반으로 반영되는 구조이므로,
     * 0.5도 전날 비중이 과하다는 판단(개발자 확정, 2026-07-15)에 따라 0.3으로 조정.
     * 이월 범위 100 — 랭킹 API의 페이지 size 상한(100)과 일치. 운영 관찰 후 조정한다.
     */
    static final double DECAY_FACTOR = 0.3;
    static final int CARRY_OVER_LIMIT = 100;

    private final RankingRepository rankingRepository;

    @Scheduled(cron = "0 50 23 * * *")
    public void carryOver() {
        try {
            LocalDate today = LocalDate.now();
            List<RankingEntry> topEntries = rankingRepository.findTopEntries(today, CARRY_OVER_LIMIT);
            if (topEntries.isEmpty()) {
                log.info("랭킹 Carry-Over 스킵 — 오늘({}) 랭킹판이 비어 있음", today);
                return;
            }

            LocalDate tomorrow = today.plusDays(1);
            for (RankingEntry entry : topEntries) {
                rankingRepository.saveScoreIfAbsent(tomorrow, entry.productId(), entry.score() * DECAY_FACTOR);
            }
            log.info("랭킹 Carry-Over 완료 — {}건을 {} 랭킹판으로 이월 (감쇠 계수 {})", topEntries.size(), tomorrow, DECAY_FACTOR);
        } catch (Exception e) {
            // 이월 실패는 콜드 스타트 완화가 하루 빠지는 것 이상으로 번지면 안 됨 — 스케줄러를 죽이지 않고 로그만 남긴다
            log.error("랭킹 Carry-Over 실패", e);
        }
    }
}
