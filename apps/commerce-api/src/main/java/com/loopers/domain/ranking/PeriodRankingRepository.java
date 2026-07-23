package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 주간·월간 랭킹 MV 조회 전용 저장소 — 적재는 commerce-batch가 담당하고, 여기서는 스냅샷을 읽기만 한다.
 * 배치는 기간별 스냅샷을 보존(히스토리)하므로, 조회는 먼저 대상 기간을 해석한 뒤 그 기간의 순위를 읽는다.
 */
public interface PeriodRankingRepository {

    /**
     * 조회 대상 스냅샷 기간을 해석한다.
     * <ul>
     *   <li>{@code date != null}: date를 포함하는(period_start ≤ date ≤ period_end) 스냅샷 중 가장 최신(period_end 최대)</li>
     *   <li>{@code date == null}: 가장 최신 스냅샷</li>
     * </ul>
     * 해당하는 스냅샷이 없으면 empty.
     */
    Optional<ResolvedPeriod> resolvePeriod(RankingPeriod period, LocalDate date);

    /** 해석된 기간 스냅샷에서 순위 오름차순으로 offset부터 limit개의 상품 ID를 조회한다. */
    List<Long> findTopProductIds(RankingPeriod period, ResolvedPeriod window, long offset, int limit);

    /** 해석된 기간 스냅샷의 전체 상품 수. */
    long countRanked(RankingPeriod period, ResolvedPeriod window);

    /** 조회 대상으로 해석된 하나의 스냅샷 기간 창. */
    record ResolvedPeriod(LocalDate periodStart, LocalDate periodEnd) {}
}
