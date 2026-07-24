package com.loopers.application.ranking;

import com.loopers.domain.ranking.ProductRankSnapshot;
import com.loopers.domain.ranking.RankingPeriod;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ProductRankAggregationProcessor {

    private static final int TOP_RANKING_LIMIT = 100;

    private final ActiveProductRepository activeProductRepository;

    public ProductRankAggregationProcessor(ActiveProductRepository activeProductRepository) {
        this.activeProductRepository = activeProductRepository;
    }

    public List<ProductRankSnapshot> aggregate(
        RankingPeriod period,
        LocalDate rankStartDate,
        LocalDate rankEndDate,
        Long batchRunId,
        List<ProductMetricInput> metrics
    ) {
        Map<Long, Double> scoreByProductId = metrics.stream()
            .collect(Collectors.groupingBy(
                ProductMetricInput::productId,
                Collectors.summingDouble(ProductMetricInput::dailyRankingScore)
            ));
        Set<Long> activeProductIds = activeProductRepository.findActiveProductIds(scoreByProductId.keySet());

        List<Map.Entry<Long, Double>> rankingEntries = scoreByProductId.entrySet().stream()
            .filter(entry -> activeProductIds.contains(entry.getKey()))
            .sorted(Map.Entry.<Long, Double>comparingByValue(Comparator.reverseOrder())
                .thenComparing(Map.Entry.comparingByKey()))
            .limit(TOP_RANKING_LIMIT)
            .toList();

        Map<Long, Double> orderedScores = new LinkedHashMap<>();
        rankingEntries.forEach(entry -> orderedScores.put(entry.getKey(), entry.getValue()));

        int[] rankNo = {1};
        return orderedScores.entrySet().stream()
            .map(entry -> ProductRankSnapshot.createInactive(
                period,
                rankStartDate,
                rankEndDate,
                batchRunId,
                entry.getKey(),
                rankNo[0]++,
                entry.getValue()
            ))
            .toList();
    }
}
