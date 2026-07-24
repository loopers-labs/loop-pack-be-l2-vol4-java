package com.loopers.application.ranking;

import com.loopers.application.product.ProductInfo;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductBrandProcessService;
import com.loopers.domain.product.ProductService;
import com.loopers.ranking.RankingPeriod;
import lombok.RequiredArgsConstructor;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Component
public class RankingFacade {
    private final RankingRepository rankingRepository;
    private final ProductService productService;
    private final BrandService brandService;
    private final ProductBrandProcessService productBrandProcessService;
    private final MeterRegistry meterRegistry;

    @Transactional(readOnly = true)
    public List<RankingInfo> getRankings(LocalDate date, int page, int size) {
        return getRankings(RankingPeriod.DAILY, date, page, size);
    }

    @Transactional(readOnly = true)
    public List<RankingInfo> getRankings(RankingPeriod period, LocalDate date, int page, int size) {
        long startedAt = System.nanoTime();
        try {
            List<RankedProduct> rankedProducts = rankingRepository.findRankedProducts(period, date, page, size);
            if (rankedProducts.isEmpty()) {
                meterRegistry.counter("ranking_query_total", "result", "miss").increment();
                return List.of();
            }
            meterRegistry.counter("ranking_query_total", "result", "hit").increment();

            List<Product> products = productService.findProductsByIds(
                rankedProducts.stream().map(RankedProduct::productId).toList()
            );
            Map<Long, Product> productsById = products.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
            List<Brand> brands = brandService.getBrandsByIds(
                products.stream().map(Product::getBrandId).distinct().toList()
            );
            Map<Long, Brand> brandsById = brands.stream()
                .collect(Collectors.toMap(Brand::getId, Function.identity()));

            return rankedProducts.stream()
                .filter(ranked -> productsById.containsKey(ranked.productId()))
                .filter(ranked -> brandsById.containsKey(productsById.get(ranked.productId()).getBrandId()))
                .map(ranked -> {
                    Product product = productsById.get(ranked.productId());
                    ProductInfo productInfo = ProductInfo.from(productBrandProcessService.getProductDetailView(
                        product,
                        brandsById.get(product.getBrandId())
                    ));
                    return new RankingInfo(ranked.rank(), ranked.score(), productInfo);
                })
                .toList();
        } finally {
            meterRegistry.timer("ranking_api_duration").record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
        }
    }
}
