package com.loopers.ranking.application;

import com.loopers.ranking.domain.RankingEntry;
import com.loopers.ranking.domain.RankingRepository;
import com.loopers.ranking.infrastructure.RankingSnapshotJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Redis 소실 복구 — 오늘 판을 어제 스냅샷 × 0.1(carry-over baseline)로 재구축한다.
 * 크래시 이전 오늘 이벤트는 되살리지 않고(근사 감수), 살아난 컨슈머가 이후 실시간으로 이어 쌓는다.
 */
@Service
@RequiredArgsConstructor
public class RankingRebuildService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final double CARRY_OVER_WEIGHT = 0.1;

    private final RankingSnapshotJpaRepository snapshotRepository;
    private final RankingRepository rankingRepository;

    public int rebuildToday() {
        LocalDate today = LocalDate.now(SEOUL);
        LocalDate yesterday = today.minusDays(1);

        List<RankingEntry> seeds = snapshotRepository.findByStatDateOrderByRankPositionAsc(yesterday).stream()
                .map(snapshot -> new RankingEntry(snapshot.getProductId(), snapshot.getScore() * CARRY_OVER_WEIGHT))
                .toList();

        rankingRepository.rebuild(today, seeds);
        return seeds.size();
    }
}
