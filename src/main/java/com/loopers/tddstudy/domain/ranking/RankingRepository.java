package com.loopers.tddstudy.domain.ranking;

import java.time.LocalDate;
import java.util.List;

public interface RankingRepository {

    void incrementScore(LocalDate date, Long productId, double delta);

    List<RankingItem> getPage(LocalDate date, int page, int size);

    Long getRank(LocalDate date, Long productId);   // 순위 없으면 null (0-based)
}
