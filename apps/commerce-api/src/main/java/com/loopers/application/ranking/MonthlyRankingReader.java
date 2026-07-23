package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.infrastructure.ranking.MvProductRankMonthlyJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * 월간 랭킹을 배치가 적재한 MV 에서 읽는다. 요청 하루를 그 달(yyyy-MM) 식별자로 변환해 조회한다(D5: 서버가 버킷 계산).
 */
@Component
@RequiredArgsConstructor
public class MonthlyRankingReader implements RankingReader {

    private final MvProductRankMonthlyJpaRepository mvRepository;

    @Override
    public RankingPeriod period() {
        return RankingPeriod.MONTHLY;
    }

    @Override
    public List<RankingEntry> topN(LocalDate date, int page, int size) {
        String yearMonth = YearMonth.from(date).toString();
        return mvRepository.findByYearMonthOrderByRankNoAsc(yearMonth, PageRequest.of(page, size)).stream()
            .map(mv -> new RankingEntry(mv.getProductId(), mv.getScore()))
            .toList();
    }
}
