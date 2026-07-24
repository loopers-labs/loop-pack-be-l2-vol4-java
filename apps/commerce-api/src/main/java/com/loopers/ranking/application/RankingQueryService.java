package com.loopers.ranking.application;

import com.loopers.product.application.ProductInfo;
import com.loopers.product.application.ProductReader;
import com.loopers.ranking.domain.RankingDatePolicy;
import com.loopers.ranking.domain.RankingEntry;
import com.loopers.ranking.domain.RankingErrorCode;
import com.loopers.ranking.domain.RankingMvRepository;
import com.loopers.ranking.domain.RankingPeriod;
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
 * 랭킹 페이지 조회. 일간은 ZSET(서빙 창 2일, Redis 장애 시 좋아요순 폴백), 주간·월간은 MV 를 읽는다.
 * 주간·월간은 확정된 지난 기간만 답하며 진행 중이거나 없는 기간은 404 다. 어느 도메인 엔티티도 밖으로 내지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingQueryService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final RankingRepository rankingRepository;
    private final RankingMvRepository rankingMvRepository;
    private final ProductReader productReader;

    /**
     * 주간·월간 조회. date 가 있으면 그 기간, 없으면 가장 최근 확정본을 준다.
     * 진행 중인 기간(아직 안 끝남)이나 배치가 안 돈 기간은 MV 를 읽지 않고 404 다.
     */
    public RankingResult.Page getPeriodRankingPage(RankingPeriod period, LocalDate date, int page, int size) {
        LocalDate today = LocalDate.now(SEOUL);
        String periodKey;
        if (date == null) {
            periodKey = period.latestCompletedKey(today);
        } else {
            if (!period.isComplete(date, today)) {
                throw new CoreException(ErrorType.NOT_FOUND, RankingErrorCode.RANKING_NOT_AVAILABLE);
            }
            periodKey = period.periodKey(date);
        }

        long total = rankingMvRepository.count(period, periodKey);
        if (total == 0) {
            throw new CoreException(ErrorType.NOT_FOUND, RankingErrorCode.RANKING_NOT_AVAILABLE);
        }

        long offset = (long) (page - 1) * size;
        List<RankingEntry> entries = rankingMvRepository.findPage(period, periodKey, offset, size);

        return new RankingResult.Page(toItems(entries, offset), total, page, size, false);
    }

    private List<RankingResult.Item> toItems(List<RankingEntry> entries, long offset) {
        if (entries.isEmpty()) {
            return List.of();
        }
        List<Long> ids = entries.stream().map(RankingEntry::productId).toList();
        Map<Long, ProductInfo> infos = productReader.getInfos(ids);

        List<RankingResult.Item> items = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            RankingEntry entry = entries.get(i);
            ProductInfo info = infos.get(entry.productId());
            if (info == null) {
                continue; // 적재 이후 삭제된 상품 — 여유분 50 이 이런 결손을 감당한다
            }
            items.add(new RankingResult.Item(offset + i + 1, entry.score(), entry.productId(), info.name(), info.brandId(), info.price()));
        }
        return items;
    }

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
