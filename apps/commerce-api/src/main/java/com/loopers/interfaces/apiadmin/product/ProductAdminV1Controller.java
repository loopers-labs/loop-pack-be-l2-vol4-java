package com.loopers.interfaces.apiadmin.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/products")
public class ProductAdminV1Controller implements ProductAdminV1ApiSpec {

    private final ProductFacade productFacade;

    @GetMapping
    @Override
    public ApiResponse<PageResponse<ProductAdminV1Dto.ProductResponse>> getProducts(
            @RequestParam(required = false) Long brandId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.success(
                PageResponse.from(productFacade.getAdminProducts(brandId, pageable)
                        .map(ProductAdminV1Dto.ProductResponse::from))
        );
    }

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<ProductAdminV1Dto.ProductResponse> getProduct(@PathVariable Long productId) {
        return ApiResponse.success(ProductAdminV1Dto.ProductResponse.from(productFacade.getProduct(productId)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public ApiResponse<ProductAdminV1Dto.ProductResponse> register(
            @Valid @RequestBody ProductAdminV1Dto.RegisterRequest request
    ) {
        return ApiResponse.success(ProductAdminV1Dto.ProductResponse.from(
                productFacade.registerProduct(request.brandId(), request.name())
        ));
    }

    @PutMapping("/{productId}")
    @Override
    public ApiResponse<ProductAdminV1Dto.ProductResponse> update(
            @PathVariable Long productId,
            @Valid @RequestBody ProductAdminV1Dto.UpdateRequest request
    ) {
        return ApiResponse.success(ProductAdminV1Dto.ProductResponse.from(
                productFacade.updateProduct(productId, request.name())
        ));
    }

    @DeleteMapping("/{productId}")
    @Override
    public ApiResponse<Void> delete(@PathVariable Long productId) {
        productFacade.deleteProduct(productId);
        return ApiResponse.success((Void) null);
    }
}
