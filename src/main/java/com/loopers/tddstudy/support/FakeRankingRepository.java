package com.loopers.tddstudy.support;

import com.loopers.tddstudy.domain.ranking.RankingItem;
import com.loopers.tddstudy.domain.ranking.RankingRepository;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FakeRankingRepository implements RankingRepository {

    // 날짜별 랭킹판: date → (productId → score)
    private final Map<LocalDate, Map<Long, Double>> boards = new HashMap<>();

    @Override
    public void incrementScore(LocalDate date, Long productId, double delta) {
        boards.computeIfAbsent(date, d -> new HashMap<>())
                .merge(productId, delta, Double::sum);
    }

    @Override
    public List<RankingItem> getPage(LocalDate date, int page, int size) {
        List<RankingItem> sorted = sortedBoard(date);
        int start = (page - 1) * size;
        if (start >= sorted.size()) return List.of();
        return sorted.subList(start, Math.min(start + size, sorted.size()));
    }

    @Override
    public Long getRank(LocalDate date, Long productId) {
        List<RankingItem> sorted = sortedBoard(date);
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).productId().equals(productId)) return (long) i;  // 0-based
        }
        return null;
    }

    private List<RankingItem> sortedBoard(LocalDate date) {
        return boards.getOrDefault(date, Map.of()).entrySet().stream()
                .map(e -> new RankingItem(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingDouble(RankingItem::score).reversed())
                .toList();
    }
}
