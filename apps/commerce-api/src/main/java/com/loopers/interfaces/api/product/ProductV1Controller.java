package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.interfaces.api.common.response.ApiResponse;
import com.loopers.interfaces.api.common.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller implements ProductV1ApiSpec {

    private final ProductFacade productFacade;

    @GetMapping("/{id}")
    @Override
    public ApiResponse<ProductV1Dto.ProductResponse> getActive(@PathVariable UUID id) {
        ProductInfo info = productFacade.getActive(id);
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(info));
    }

    @GetMapping
    @Override
    public ApiResponse<PageResponse<ProductV1Dto.ProductResponse>> getActiveList(
        @RequestParam(required = false) UUID brandId,
        @RequestParam(required = false) String sort,
        Pageable pageable
    ) {
        Pageable resolvedPageable = PageRequest.of(
            pageable.getPageNumber(),
            pageable.getPageSize(),
            ProductSortType.resolve(sort)
        );
        Page<ProductInfo> page = productFacade.getActiveList(brandId, resolvedPageable);
        return ApiResponse.success(PageResponse.from(page.map(ProductV1Dto.ProductResponse::from)));
    }
}
