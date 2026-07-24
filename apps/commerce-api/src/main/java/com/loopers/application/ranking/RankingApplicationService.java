package com.loopers.application.ranking;

import com.loopers.application.product.ProductInfo;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.infrastructure.product.ProductCacheStore;
import com.loopers.infrastructure.ranking.ProductRankMvStore;
import com.loopers.infrastructure.ranking.RankingRedisStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 랭킹 조회 유스케이스.
 *
 * <p>Redis ZSET 에서 순위 구간의 productId 를 받아 순서를 확정한 뒤, 상품 정보는 상품 목록 조회
 * ({@code ProductApplicationService#getProducts})와 동일하게 {@link ProductCacheStore} 상세 캐시를
 * <strong>읽기 전용</strong>으로 재사용한다(캐시 히트 시 DB 를 타지 않음). 순위(순서) 자체는 항상 ZSET
 * 라이브 조회지만, 재고를 포함한 상품 정보는 목록 화면 관례대로 캐시된 값을 그대로 쓴다 — 랭킹은
 * "지금 살지 결정하는" 상세화면이 아니라 목록 화면이라, 상세조회처럼 재고를 매번 라이브로 재조회할 이유가 없다.
 * 캐시 미스 시에는 상세조회와 달리 브랜드명 없는 {@code forUserList} 로 조립하는데, 이 값을 공유 상세 캐시에
 * 다시 쓰면(putDetail) 브랜드명 null 로 캐시를 오염시키므로 <strong>이번 응답에만 쓰고 캐시에는 쓰지 않는다</strong>.
 * 랭킹은 상위 {@value #TOP_N} 위까지만 노출한다.
 */
@RequiredArgsConstructor
@Service
public class RankingApplicationService {

    /** 랭킹 노출 상한 — 이 순위를 넘는 구간 요청은 빈 목록으로 응답한다. */
    private static final int TOP_N = 100;

    private final RankingRedisStore rankingRedisStore;
    private final ProductRankMvStore productRankMvStore;
    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final ProductCacheStore productCacheStore;

    @Transactional(readOnly = true)
    public List<RankingInfo> getRanking(LocalDate date, int page, int size) {
        return getRanking(date, RankingPeriod.DAILY, page, size);
    }

    @Transactional(readOnly = true)
    public List<RankingInfo> getRanking(LocalDate date, String period, int page, int size) {
        return getRanking(date, RankingPeriod.from(period), page, size);
    }

    @Transactional(readOnly = true)
    public List<RankingInfo> getRanking(LocalDate date, RankingPeriod period, int page, int size) {
        if (page < 1 || size < 1) {
            return List.of();   // 잘못된 페이지 요청 — ZREVRANGE 음수 인덱스로 새지 않도록 방어
        }
        long start = (long) (page - 1) * size;
        if (start >= TOP_N) {
            return List.of();   // Top-100 밖 구간 — 에러 대신 빈 목록
        }
        long end = Math.min(start + size - 1, TOP_N - 1);
        List<Long> productIds = switch (period) {
            case DAILY -> rankingRedisStore.findProductIdsByRank(date, start, end);
            case WEEKLY, MONTHLY -> productRankMvStore.findProductIds(period, date, start, end);
        };
        return assemble(productIds, start);
    }

    /**
     * 시간단위 랭킹 — carry-over 없이 매 시간 0점부터 시작한다("지금 뜨는 상품" 용도, 의도된 콜드스타트).
     */
    @Transactional(readOnly = true)
    public List<RankingInfo> getHourlyRanking(LocalDateTime hour, int page, int size) {
        if (page < 1 || size < 1) {
            return List.of();
        }
        long start = (long) (page - 1) * size;
        if (start >= TOP_N) {
            return List.of();
        }
        long end = Math.min(start + size - 1, TOP_N - 1);
        return assemble(rankingRedisStore.findProductIdsByHour(hour, start, end), start);
    }

    private List<RankingInfo> assemble(List<Long> orderedIds, long start) {
        if (orderedIds.isEmpty()) {
            return List.of();
        }

        Map<Long, ProductInfo> infoMap = new HashMap<>();
        List<Long> missedIds = new ArrayList<>();
        for (Long productId : orderedIds) {
            productCacheStore.getDetail(productId).ifPresentOrElse(
                info -> infoMap.put(productId, info),
                () -> missedIds.add(productId));
        }
        if (!missedIds.isEmpty()) {
            Map<Long, ProductModel> productMap = productRepository.findAllByIds(missedIds).stream()
                .collect(Collectors.toMap(ProductModel::getId, Function.identity()));
            Map<Long, StockModel> missedStockMap = stockRepository.findAllByProductIdIn(missedIds).stream()
                .collect(Collectors.toMap(StockModel::getProductId, Function.identity()));
            for (Long productId : missedIds) {
                ProductModel product = productMap.get(productId);
                StockModel stock = missedStockMap.get(productId);
                if (product == null || stock == null) {
                    continue;   // 삭제됐거나 정합성 공백인 상품 — 건너뜀(순위 번호는 ZSET 위치 유지)
                }
                // 주의: forUserList 는 brandName 이 없다 — 상세조회와 공유하는 product:detail 캐시에 이 값을
                // putDetail 하면 브랜드명이 null 인 채로 캐시가 오염되어 이후 상세조회 응답이 깨진다.
                // 그래서 이 값은 이번 응답 조립에만 쓰고 캐시에는 쓰지 않는다.
                infoMap.put(productId, ProductInfo.forUserList(product, stock));
            }
        }

        List<RankingInfo> result = new ArrayList<>(orderedIds.size());
        for (int i = 0; i < orderedIds.size(); i++) {
            Long productId = orderedIds.get(i);
            ProductInfo info = infoMap.get(productId);
            if (info == null) {
                continue;   // 삭제됐거나 정합성 공백인 상품은 건너뛴다(순위 번호는 ZSET 위치 유지)
            }
            int rank = (int) (start + i + 1);
            result.add(new RankingInfo(rank, info));
        }
        return result;
    }
}
