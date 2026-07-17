package com.loopers.product.interfaces.api;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.product.application.ProductCommand;
import com.loopers.product.application.ProductReadCacheService;
import com.loopers.product.application.ProductResult;
import com.loopers.product.application.event.ProductViewedEvent;
import com.loopers.product.domain.ProductSortOption;
import com.loopers.ranking.application.RankingReader;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller implements ProductV1ApiSpec {

    private final ProductReadCacheService productReadCacheService;
    private final RankingReader rankingReader;
    private final ApplicationEventPublisher eventPublisher;

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<ProductV1Response.Detail> get(@PathVariable("productId") Long productId) {
        ProductResult.Detail result = productReadCacheService.getProduct(productId);
        Integer todayRank = rankingReader.todayRank(productId); // 순위는 실시간이라 상품 캐시 밖에서 조회
        eventPublisher.publishEvent(new ProductViewedEvent(productId)); // 조회 집계는 비동기 best-effort
        return ApiResponse.success(ProductV1Response.Detail.from(result, todayRank));
    }

    @GetMapping
    @Override
    public ApiResponse<ProductV1Response.Page> getAll(
        @RequestParam(name = "brandId", required = false) Long brandId,
        @RequestParam(name = "sort", defaultValue = "LATEST") ProductSortOption sort,
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        ProductResult.Page result = productReadCacheService.getProducts(
                new ProductCommand.PageQuery(brandId, sort, page, size));
        return ApiResponse.success(ProductV1Response.Page.from(result));
    }
}
