package com.loopers.ranking.application;

import com.loopers.product.application.ProductInfo;
import com.loopers.product.application.ProductReader;
import com.loopers.ranking.domain.RankingDatePolicy;
import com.loopers.ranking.domain.RankingEntry;
import com.loopers.ranking.domain.RankingErrorCode;
import com.loopers.ranking.domain.RankingRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 랭킹 페이지 조회. 서빙 창(최근 2일)을 벗어나면 404, 창 안이면 ZSET 을 읽어 상품정보를 조합한다.
 * Redis 장애 시 좋아요순(DB) 폴백으로 degraded 응답을 준다. 어느 도메인 엔티티도 밖으로 내지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingQueryService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final RankingRepository rankingRepository;
    private final ProductReader productReader;

    public RankingResult.Page getRankingPage(LocalDate date, int page, int size) {
        LocalDate today = LocalDate.now(SEOUL);
        if (!RankingDatePolicy.isServable(date, today)) {
            throw new CoreException(ErrorType.NOT_FOUND, RankingErrorCode.RANKING_NOT_AVAILABLE);
        }

        long start = (long) (page - 1) * size;
        long end = start + size - 1;

        List<RankingEntry> entries;
        long total;
        try {
            entries = rankingRepository.range(date, start, end);
            total = rankingRepository.size(date);
        } catch (DataAccessException e) {
            log.warn("ranking read fallback reason=redis_error date={} page={}", date, page, e);
            return fallbackByLikes(page, size);
        }

        if (entries.isEmpty()) {
            return new RankingResult.Page(List.of(), total, page, size, false);
        }

        List<Long> ids = entries.stream().map(RankingEntry::productId).toList();
        Map<Long, ProductInfo> infos = productReader.getInfos(ids);

        List<RankingResult.Item> items = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            RankingEntry entry = entries.get(i);
            ProductInfo info = infos.get(entry.productId());
            if (info == null) {
                continue; // 삭제/미노출 상품은 랭킹 표시에서 제외
            }
            items.add(new RankingResult.Item(start + i + 1, entry.score(), entry.productId(), info.name(), info.brandId(), info.price()));
        }
        return new RankingResult.Page(items, total, page, size, false);
    }

    private RankingResult.Page fallbackByLikes(int page, int size) {
        Map<Long, ProductInfo> top = productReader.getTopByLikes(size);
        List<RankingResult.Item> items = new ArrayList<>();
        long rank = 1;
        for (Map.Entry<Long, ProductInfo> entry : top.entrySet()) {
            ProductInfo info = entry.getValue();
            items.add(new RankingResult.Item(rank++, null, entry.getKey(), info.name(), info.brandId(), info.price()));
        }
        return new RankingResult.Page(items, items.size(), page, size, true);
    }
}
