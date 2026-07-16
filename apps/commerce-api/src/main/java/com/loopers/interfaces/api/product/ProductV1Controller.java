package com.loopers.interfaces.api.product;

import com.loopers.application.activity.UserActivityEvent;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.application.ranking.RankingFacade;
import com.loopers.domain.product.ProductSortType;
import com.loopers.domain.ranking.Rank;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller implements ProductV1ApiSpec {

    private final ProductFacade productFacade;
    private final RankingFacade rankingFacade;
    private final ApplicationEventPublisher eventPublisher;

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<ProductV1Dto.ProductResponse> getProduct(
        @PathVariable(value = "productId") Long productId
    ) {
        ProductInfo info = productFacade.getProduct(productId);
        // 조회는 익명 read(액터 미상) + @Cacheable(캐시 히트 시 facade body skip)이라, 컨트롤러에서 관측 이벤트를 발행한다.
        eventPublisher.publishEvent(UserActivityEvent.of(null, UserActivityEvent.Type.PRODUCT_VIEWED, productId));
        // 순위는 캐시된 상품 밖에서 신선하게 조합한다(자주 바뀌는 값이라 캐시에 굳으면 stale). 랭킹 밖이면 null.
        Long rank = rankingFacade.currentRankOf(productId).map(Rank::value).orElse(null);
        ProductV1Dto.ProductResponse response = ProductV1Dto.ProductResponse.from(info, rank);
        return ApiResponse.success(response);
    }

    @GetMapping
    @Override
    public ApiResponse<List<ProductV1Dto.ProductResponse>> getAllProducts(
        @RequestParam(required = false) Long brandId,
        @RequestParam(defaultValue = "LATEST") ProductSortType sort,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        List<ProductInfo> infos = productFacade.getAllProducts(brandId, sort, page, size);
        // 목록 브라우즈도 관측 대상. targetId 는 필터로 쓴 brandId(없으면 null).
        eventPublisher.publishEvent(UserActivityEvent.of(null, UserActivityEvent.Type.PRODUCT_BROWSED, brandId));
        List<ProductV1Dto.ProductResponse> responses = infos.stream()
            .map(ProductV1Dto.ProductResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }
}
