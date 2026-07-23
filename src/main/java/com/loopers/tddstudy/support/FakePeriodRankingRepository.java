package com.loopers.tddstudy.support;

import com.loopers.tddstudy.domain.ranking.PeriodRankingRepository;
import com.loopers.tddstudy.domain.ranking.RankingItem;

import java.util.*;

public class FakePeriodRankingRepository implements PeriodRankingRepository {

    private final Map<String, List<RankingItem>> weekly = new HashMap<>();
    private final Map<String, List<RankingItem>> monthly = new HashMap<>();

    public void addWeekly(String periodKey, Long productId, double score) {
        weekly.computeIfAbsent(periodKey, k -> new ArrayList<>()).add(new RankingItem(productId, score));
    }

    public void addMonthly(String periodKey, Long productId, double score) {
        monthly.computeIfAbsent(periodKey, k -> new ArrayList<>()).add(new RankingItem(productId, score));
    }

    @Override
    public List<RankingItem> getWeeklyPage(String periodKey, int page, int size) {
        return pageOf(weekly.getOrDefault(periodKey, List.of()), page, size);
    }

    @Override
    public List<RankingItem> getMonthlyPage(String periodKey, int page, int size) {
        return pageOf(monthly.getOrDefault(periodKey, List.of()), page, size);
    }

    private List<RankingItem> pageOf(List<RankingItem> all, int page, int size) {
        int start = (page - 1) * size;
        if (start >= all.size()) return List.of();
        return all.subList(start, Math.min(start + size, all.size()));
    }
}
