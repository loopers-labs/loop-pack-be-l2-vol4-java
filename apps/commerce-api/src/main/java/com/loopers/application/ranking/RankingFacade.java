package com.loopers.application.ranking;

import com.loopers.application.product.ProductInfo;
import com.loopers.application.support.PageResult;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.domain.ranking.RankingRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class RankingFacade {

    // period -> 저장소. 분기는 여기 한 곳에만 있고, 아래 조합 로직은 저장소가 뭐든 동일하다 (week10 qna Q3).
    private final Map<RankingPeriod, RankingRepository> rankingRepositories;
    private final ProductService productService;
    private final BrandService brandService;

    public RankingFacade(List<RankingRepository> rankingRepositories, ProductService productService, BrandService brandService) {
        this.rankingRepositories = rankingRepositories.stream()
            .collect(Collectors.toMap(RankingRepository::period, Function.identity()));
        this.productService = productService;
        this.brandService = brandService;
    }

    public PageResult<RankingInfo> getRankings(RankingPeriod period, LocalDate date, int page, int size) {
        RankingRepository rankingRepository = rankingRepositories.get(period);
        long start = (long) (page - 1) * size;

        List<Long> productIds = rankingRepository.findProductIds(date, page, size);
        Map<Long, Product> productsById = productService.getProductsByIds(productIds).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<RankingInfo> items = new ArrayList<>();
        for (int i = 0; i < productIds.size(); i++) {
            Product product = productsById.get(productIds.get(i));
            if (product == null) {
                continue; // 랭킹엔 있지만 상품이 삭제된 경우 등 — 두 데이터 소스 간 불일치는 방어적으로 skip
            }
            Brand brand = brandService.getBrand(product.getBrandId());
            long rank = start + i + 1; // 저장소는 0-based 순위이므로 표시용 순위는 +1
            items.add(new RankingInfo(rank, ProductInfo.from(product, brand)));
        }

        long totalElements = rankingRepository.count(date);
        return new PageResult<>(items, page, size, totalElements);
    }

    public Optional<Long> getRank(LocalDate date, Long productId) {
        // 상품 상세에 노출하는 순위는 일간 기준이다.
        return rankingRepositories.get(RankingPeriod.DAILY).findRank(date, productId).map(rank -> rank + 1); // 0-indexed → 표시용 순위
    }
}
