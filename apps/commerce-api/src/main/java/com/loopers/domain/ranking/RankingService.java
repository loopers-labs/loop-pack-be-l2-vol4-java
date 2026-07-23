package com.loopers.domain.ranking;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RankingService {

    private final ProductRanking productRanking;
    private final PeriodicRanking periodicRanking;

    public List<RankedProduct> getPage(LocalDate date, Pageable pageable) {
        return productRanking.page(date, pageable.getPageNumber(), pageable.getPageSize());
    }

    public long count(LocalDate date) {
        return productRanking.totalCount(date);
    }

    public Optional<Long> getRank(LocalDate date, Long productId) {
        return productRanking.rankOf(date, productId);
    }

    @Transactional(readOnly = true)
    public List<PeriodRankedProduct> getPeriodPage(RankingPeriod period, LocalDate date, Pageable pageable) {
        return periodicRanking.page(period, date, pageable.getPageNumber(), pageable.getPageSize());
    }

    @Transactional(readOnly = true)
    public long countPeriod(RankingPeriod period, LocalDate date) {
        return periodicRanking.totalCount(period, date);
    }
}
