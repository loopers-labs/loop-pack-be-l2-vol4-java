package com.loopers.application.ranking;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductMetricsService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.ranking.RankedProduct;
import com.loopers.domain.ranking.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 오늘의 인기상품(랭킹) 유스케이스 조립. 랭킹 ZSET(순위·스코어)과 상품 상세(이름/가격/브랜드/좋아요수)를
 * 조합해 응답용 {@link RankingPageInfo} 를 만든다.
 *
 * <p><b>N+1 회피</b>: 페이지의 productId 를 모아 상품/좋아요수/브랜드명을 각각 한 번의 batch 조회로 가져온다.
 * ZSET 엔 남아있지만 그 사이 <b>삭제/비활성된 상품</b>은 조회 결과에서 빠지므로(활성 상품만 노출) 랭킹에서 제외한다.
 */
@Component
@RequiredArgsConstructor
public class RankingFacade {

    private final RankingRepository rankingRepository;
    private final ProductService productService;
    private final ProductMetricsService productMetricsService;
    private final BrandService brandService;

    public RankingPageInfo getRanking(LocalDate date, int page, int size) {
        List<RankedProduct> ranked = rankingRepository.findPage(date, page, size);
        long totalCount = rankingRepository.size(date);
        if (ranked.isEmpty()) {
            return new RankingPageInfo(List.of(), totalCount, page, size);
        }

        List<Long> productIds = ranked.stream().map(RankedProduct::productId).toList();

        Map<Long, ProductModel> productsById = productService.findActiveByIds(productIds).stream()
                .collect(Collectors.toMap(ProductModel::getId, Function.identity()));
        Map<Long, Long> likeCounts = productMetricsService.getLikeCounts(productIds);

        Set<Long> brandIds = productsById.values().stream()
                .map(ProductModel::getBrandId)
                .collect(Collectors.toSet());
        Map<Long, String> brandNames = brandService.findByIds(brandIds).stream()
                .collect(Collectors.toMap(BrandModel::getId, BrandModel::getName));

        List<RankedProductInfo> items = new ArrayList<>(ranked.size());
        for (RankedProduct r : ranked) {
            ProductModel product = productsById.get(r.productId());
            if (product == null) {
                continue; // 삭제/비활성 상품 — 랭킹 노출 제외
            }
            items.add(new RankedProductInfo(
                    r.rank(),
                    product.getId(),
                    product.getName(),
                    product.getPrice(),
                    product.getBrandId(),
                    brandNames.get(product.getBrandId()),
                    likeCounts.getOrDefault(product.getId(), 0L),
                    r.score()
            ));
        }
        return new RankingPageInfo(items, totalCount, page, size);
    }
}
