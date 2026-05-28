package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.interfaces.api.common.response.ApiResponse;
import com.loopers.interfaces.api.common.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/products")
public class ProductAdminV1Controller implements ProductAdminV1ApiSpec {

    private final ProductFacade productFacade;

    @PostMapping
    @Override
    public ApiResponse<ProductV1Dto.AdminProductResponse> create(@RequestBody @Valid ProductV1Dto.CreateRequest request) {
        ProductInfo info = productFacade.create(
            request.brandId(), request.name(), request.description(), request.price(), request.initialQuantity()
        );
        return ApiResponse.success(ProductV1Dto.AdminProductResponse.from(info));
    }

    @GetMapping("/{id}")
    @Override
    public ApiResponse<ProductV1Dto.AdminProductResponse> get(@PathVariable UUID id) {
        return ApiResponse.success(ProductV1Dto.AdminProductResponse.from(productFacade.get(id)));
    }

    @GetMapping
    @Override
    public ApiResponse<PageResponse<ProductV1Dto.AdminProductResponse>> getList(
        @RequestParam(required = false) UUID brandId,
        Pageable pageable
    ) {
        Page<ProductInfo> page = productFacade.getList(brandId, pageable);
        return ApiResponse.success(PageResponse.from(page.map(ProductV1Dto.AdminProductResponse::from)));
    }

    @PutMapping("/{id}")
    @Override
    public ApiResponse<ProductV1Dto.AdminProductResponse> update(
        @PathVariable UUID id,
        @RequestBody @Valid ProductV1Dto.UpdateRequest request
    ) {
        ProductInfo info = productFacade.update(id, request.name(), request.description(), request.price());
        return ApiResponse.success(ProductV1Dto.AdminProductResponse.from(info));
    }

    @DeleteMapping("/{id}")
    @Override
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        productFacade.delete(id);
        return ApiResponse.success(null);
    }
}
