package com.loopers.tddstudy.application.ranking;

import com.loopers.tddstudy.domain.product.Product;
import com.loopers.tddstudy.domain.product.ProductRepository;
import com.loopers.tddstudy.domain.ranking.PeriodRankingRepository;
import com.loopers.tddstudy.domain.ranking.RankingItem;
import com.loopers.tddstudy.domain.ranking.RankingPeriod;
import com.loopers.tddstudy.domain.ranking.RankingPeriodType;
import com.loopers.tddstudy.domain.ranking.RankingRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class RankingService {

    private final RankingRepository rankingRepository;
    private final PeriodRankingRepository periodRankingRepository;
    private final ProductRepository productRepository;

    public RankingService(RankingRepository rankingRepository,
                          PeriodRankingRepository periodRankingRepository,
                          ProductRepository productRepository) {
        this.rankingRepository = rankingRepository;
        this.periodRankingRepository = periodRankingRepository;
        this.productRepository = productRepository;
    }

    /** 기존 호출부 호환용 — 일간 랭킹 */
    public List<RankingInfo> getRankingPage(LocalDate date, int page, int size) {
        return getRankingPage(RankingPeriodType.DAILY, date, page, size);
    }

    public List<RankingInfo> getRankingPage(RankingPeriodType type, LocalDate date, int page, int size) {
        List<RankingItem> items = switch (type) {
            case DAILY   -> rankingRepository.getPage(date, page, size);
            case WEEKLY  -> periodRankingRepository.getWeeklyPage(
                                    RankingPeriod.lastCompletedWeek(date).key(), page, size);
            case MONTHLY -> periodRankingRepository.getMonthlyPage(
                                    RankingPeriod.lastCompletedMonth(date).key(), page, size);
        };
        return withProductInfo(items, page, size);
    }

    // 상품 정보를 붙이고 순위 번호를 매긴다
    private List<RankingInfo> withProductInfo(List<RankingItem> items, int page, int size) {
        long startRank = (long) (page - 1) * size + 1;   // 이 페이지 첫 항목의 순위
        List<RankingInfo> result = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            RankingItem item = items.get(i);
            Product product = productRepository.findById(item.productId()).orElse(null);
            if (product == null) {
                continue;   // 랭킹엔 있지만 DB에서 사라진 상품 → 건너뛰되 순위 번호는 유지
            }
            result.add(new RankingInfo(
                    startRank + i,          // 건너뛴 자리는 비워둠 (1, 3, ...)
                    item.productId(),
                    product.getName(),
                    product.getPrice(),
                    item.score()
            ));
        }
        return result;
    }

    // 특정 상품의 순위 (1-based). 랭킹에 없으면 null.
    public Long getRank(LocalDate date, Long productId) {
        Long rank = rankingRepository.getRank(date, productId);   // 0-based, 없으면 null
        return (rank == null) ? null : rank + 1;
    }
}
