package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.domain.product.ProductViewedEvent;
import com.loopers.interfaces.api.ApiResponse;
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

    private final ProductFacade productFacade;
    private final ApplicationEventPublisher eventPublisher;

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<ProductV1Dto.ProductResponse> getProduct(
        @PathVariable(value = "productId") Long productId
    ) {
        // 조회 사실은 캐시 적중 여부와 무관하게 발생하므로, @Cacheable(파사드) 바깥인 여기서 발행한다.
        eventPublisher.publishEvent(new ProductViewedEvent(productId));
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(productFacade.getProduct(productId)));
    }

    @GetMapping
    @Override
    public ApiResponse<ProductV1Dto.ProductPageResponse> getProducts(
        @RequestParam(value = "brandId", required = false) Long brandId,
        @RequestParam(value = "sort", required = false, defaultValue = "latest") String sort,
        @RequestParam(value = "page", required = false, defaultValue = "0") int page,
        @RequestParam(value = "size", required = false, defaultValue = "20") int size
    ) {
        return ApiResponse.success(ProductV1Dto.ProductPageResponse.from(productFacade.getProducts(brandId, sort, page, size)));
    }
}
