package com.loopers.domain.ranking;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RankingService {

    private final ProductRanking productRanking;

    public List<RankedProduct> getPage(LocalDate date, Pageable pageable) {
        return productRanking.page(date, pageable.getPageNumber(), pageable.getPageSize());
    }

    public long count(LocalDate date) {
        return productRanking.totalCount(date);
    }

    public Optional<Long> getRank(LocalDate date, Long productId) {
        return productRanking.rankOf(date, productId);
    }
}
