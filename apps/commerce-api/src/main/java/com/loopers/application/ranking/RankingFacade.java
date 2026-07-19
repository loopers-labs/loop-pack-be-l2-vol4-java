package com.loopers.application.ranking;

import com.loopers.application.product.ProductInfo;
import com.loopers.application.support.PageResult;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.ranking.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class RankingFacade {

    private final RankingRepository rankingRepository;
    private final ProductService productService;
    private final BrandService brandService;

    public PageResult<RankingInfo> getRankings(LocalDate date, int page, int size) {
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
            long rank = start + i + 1; // ZREVRANGE 는 0부터 시작하므로 표시용 순위는 +1
            items.add(new RankingInfo(rank, ProductInfo.from(product, brand)));
        }

        long totalElements = rankingRepository.count(date);
        return new PageResult<>(items, page, size, totalElements);
    }

    public Optional<Long> getRank(LocalDate date, Long productId) {
        return rankingRepository.findRank(date, productId).map(rank -> rank + 1); // 0-indexed → 표시용 순위
    }
}
