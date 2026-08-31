package com.loopers.application.ranking;

import com.loopers.domain.product.ProductCacheDto;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.ranking.MvRankingService;
import com.loopers.domain.ranking.RankingService;
import com.loopers.domain.ranking.RankingType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class RankingFacade {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    private final RankingService rankingService;
    private final ProductService productService;
    private final MvRankingService mvRankingService;

    public Page<RankingInfo> getTopRanked(RankingType type, String date, int page, int size) {
        if (type == RankingType.WEEKLY) {
            return paginate(mvRankingService.getWeeklyRankedAll(toYearWeek(date)), page, size);
        }
        if (type == RankingType.MONTHLY) {
            return paginate(mvRankingService.getMonthlyRankedAll(toYearMonth(date)), page, size);
        }

        List<UUID> productIds = rankingService.getTopRanked(type, date, page, size);
        long total = rankingService.countRanked(type, date);
        int startRank = (page - 1) * size + 1;

        List<RankingInfo> result = new ArrayList<>();
        for (int i = 0; i < productIds.size(); i++) {
            UUID productId = productIds.get(i);
            try {
                ProductCacheDto product = productService.getActiveSnapshot(productId);
                Double score = rankingService.getScore(type, date, productId);
                result.add(new RankingInfo(startRank + i, score, product.id(), product.name(), product.brandName(), product.price()));
            } catch (CoreException e) {
                if (e.getErrorType() == ErrorType.NOT_FOUND) {
                    continue;
                }
                throw e;
            }
        }
        return new PageImpl<>(result, PageRequest.of(page - 1, size), total);
    }

    public Long getRank(String date, UUID productId) {
        return rankingService.getRank(RankingType.DAILY, date, productId);
    }

    private Page<RankingInfo> paginate(List<RankingInfo> all, int page, int size) {
        int from = (page - 1) * size;
        if (from >= all.size()) {
            return new PageImpl<>(List.of(), PageRequest.of(page - 1, size), all.size());
        }
        return new PageImpl<>(all.subList(from, Math.min(from + size, all.size())), PageRequest.of(page - 1, size), all.size());
    }

    private int toYearWeek(String date) {
        LocalDate d = LocalDate.parse(date, DATE_FORMAT);
        int year = d.get(WeekFields.ISO.weekBasedYear());
        int week = d.get(WeekFields.ISO.weekOfWeekBasedYear());
        return year * 100 + week;
    }

    private int toYearMonth(String date) {
        YearMonth ym = YearMonth.parse(date, MONTH_FORMAT);
        return ym.getYear() * 100 + ym.getMonthValue();
    }
}
