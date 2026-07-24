package com.loopers.application.ranking;

import com.loopers.application.product.ProductService;
import com.loopers.domain.product.Product;
import com.loopers.domain.ranking.RankingItem;
import com.loopers.domain.ranking.RankingMvPeriod;
import com.loopers.domain.ranking.RankingMvReadRepository;
import com.loopers.domain.ranking.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class RankingFacade {

    // 삭제된 상품으로 빠진 자리를 채우기 위해 추가로 조회할 페이지 수 상한.
    // 삭제 빈도가 낮다는 전제(하루 수백 건 수준)라 한두 페이지면 충분하지만,
    // 특정 날짜의 상품이 대거 삭제된 극단적인 경우에도 조회가 무한 반복되지 않도록 안전장치를 둔다.
    private static final int MAX_BACKFILL_PAGES = 5;

    private final RankingRepository rankingRepository;
    private final RankingMvReadRepository rankingMvReadRepository;
    private final ProductService productService;
    private final Clock clock;

    /**
     * 일간 랭킹 페이지를 상품정보와 함께 조회한다.
     * 순위권 내 상품이 삭제되어 제외되면, 요청한 size를 채울 때까지 다음 페이지를 추가로 조회해 보정한다.
     *
     * @param date 조회 날짜 (null이면 오늘)
     * @param page 0-based 페이지 번호
     */
    public RankingInfo getRankings(LocalDate date, int page, int size) {
        LocalDate targetDate = date != null ? date : LocalDate.now(clock);
        long totalCount = rankingRepository.countTotal(targetDate);
        List<RankingInfo.RankingProductInfo> items = collectWithBackfill(
            page, size, currentPage -> rankingRepository.findPage(targetDate, currentPage, size)
        );
        return RankingInfo.ofDate(targetDate, totalCount, items);
    }

    /**
     * 주간/월간 랭킹(MV) 페이지를 상품정보와 함께 조회한다. 순위 산정 방식은 일간과 동일하게
     * 절대 위치 기준으로 매기고, 삭제된 상품은 같은 방식으로 보정한다.
     *
     * @param page 0-based 페이지 번호
     */
    public RankingInfo getRankings(RankingMvPeriod period, String periodKey, int page, int size) {
        long totalCount = rankingMvReadRepository.countTotal(period, periodKey);
        List<RankingInfo.RankingProductInfo> items = collectWithBackfill(
            page, size, currentPage -> rankingMvReadRepository.findPage(period, periodKey, currentPage, size)
        );
        return RankingInfo.ofPeriod(period, periodKey, totalCount, items);
    }

    private List<RankingInfo.RankingProductInfo> collectWithBackfill(
        int page, int size, IntFunction<List<RankingItem>> pageFetcher
    ) {
        List<RankingInfo.RankingProductInfo> items = new ArrayList<>();
        int currentPage = page;
        for (int fetched = 0; items.size() < size && fetched < MAX_BACKFILL_PAGES; fetched++) {
            List<RankingItem> rankingItems = pageFetcher.apply(currentPage);
            if (rankingItems.isEmpty()) {
                break;
            }

            List<Long> productIds = rankingItems.stream().map(RankingItem::productId).toList();
            Map<Long, Product> products = productService.getProductsByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

            // 랭킹 순위는 페이지 내 위치가 아니라 전체 기준 절대 위치로 매긴다.
            long offset = (long) currentPage * size;
            for (int i = 0; i < rankingItems.size() && items.size() < size; i++) {
                RankingItem rankingItem = rankingItems.get(i);
                Product product = products.get(rankingItem.productId());
                if (product == null) {
                    continue; // 삭제된 상품은 제외 (순위는 원래 위치 기준 유지)
                }
                items.add(RankingInfo.RankingProductInfo.of(offset + i + 1, product, rankingItem.score()));
            }
            currentPage++;
        }
        return items;
    }
}
