package com.loopers.ranking.application;

import com.loopers.ranking.RankingPeriod;
import com.loopers.shared.pagination.PageQuery;
import com.loopers.shared.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

@Slf4j
@RequiredArgsConstructor
@Service
public class RankingReadService {

    private final DailyRankingQuery dailyRankingQuery;
    private final PublishedRankingQuery publishedRankingQuery;
    private final RankingMetrics rankingMetrics;

    public PageResult<RankingPosition> getRankings(
        RankingPeriod period,
        LocalDate date,
        PageQuery pageQuery
    ) {
        return switch (period) {
            case DAILY -> getDailyRanking(date, pageQuery);
            case WEEKLY, MONTHLY -> getPublishedRanking(period, date, pageQuery);
        };
    }

    private PageResult<RankingPosition> getDailyRanking(LocalDate date, PageQuery pageQuery) {
        long start = (long) pageQuery.page() * pageQuery.size();
        long end = start + pageQuery.size() - 1;
        DailyRankingEntries entries = dailyRankingQuery.findDaily(date, start, end);

        return pageResult(
            positions(entries.productIds(), start),
            entries.totalElements(),
            pageQuery
        );
    }

    private PageResult<RankingPosition> getPublishedRanking(
        RankingPeriod period,
        LocalDate date,
        PageQuery pageQuery
    ) {
        Optional<PublishedRanking> publishedRanking =
            publishedRankingQuery.findLatestCompleted(period, date);
        if (publishedRanking.isEmpty()) {
            rankingMetrics.recordPublishedSnapshotMissing(period);
            log.warn("Completed ranking snapshot is missing. period={}, date={}", period, date);
            return pageResult(List.of(), 0, pageQuery);
        }

        List<RankingPosition> positions = publishedRanking.orElseThrow().positions();
        if (positions.isEmpty()) {
            rankingMetrics.recordPublishedSnapshotEmpty(period);
            log.debug("Completed ranking snapshot is empty. period={}, date={}", period, date);
        }
        return pageResult(pageContent(positions, pageQuery), positions.size(), pageQuery);
    }

    public Optional<Long> getDailyRank(LocalDate date, Long productId) {
        return dailyRankingQuery.findDailyRank(date, productId)
            .map(position -> position + 1);
    }

    private List<RankingPosition> positions(List<Long> productIds, long start) {
        return IntStream.range(0, productIds.size())
            .mapToObj(index -> new RankingPosition(start + index + 1, productIds.get(index)))
            .toList();
    }

    private List<RankingPosition> pageContent(
        List<RankingPosition> positions,
        PageQuery pageQuery
    ) {
        long start = (long) pageQuery.page() * pageQuery.size();
        if (start >= positions.size()) {
            return List.of();
        }

        int fromIndex = Math.toIntExact(start);
        int toIndex = Math.min(fromIndex + pageQuery.size(), positions.size());
        return positions.subList(fromIndex, toIndex);
    }

    private PageResult<RankingPosition> pageResult(
        List<RankingPosition> content,
        long totalElements,
        PageQuery pageQuery
    ) {
        int totalPages = totalPages(totalElements, pageQuery.size());
        return new PageResult<>(
            content,
            totalElements,
            totalPages,
            pageQuery.page(),
            pageQuery.size(),
            pageQuery.page() == 0,
            totalPages == 0 || pageQuery.page() >= totalPages - 1
        );
    }

    private int totalPages(long totalElements, int size) {
        if (totalElements == 0) {
            return 0;
        }
        return Math.toIntExact(((totalElements - 1) / size) + 1);
    }
}
