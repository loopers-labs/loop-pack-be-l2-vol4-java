package com.loopers.tddstudy.domain.ranking;

import java.util.List;

public interface PeriodRankingRepository {

    List<RankingItem> getWeeklyPage(String periodKey, int page, int size);

    List<RankingItem> getMonthlyPage(String periodKey, int page, int size);
}
