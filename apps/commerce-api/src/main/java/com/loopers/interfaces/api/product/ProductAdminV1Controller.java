package com.loopers.interfaces.api.product;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.product.ProductCreateInfo;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductUpdateInfo;
import com.loopers.interfaces.api.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api-admin/v1/products")
public class ProductAdminV1Controller implements ProductAdminV1ApiSpec {

    private final ProductFacade productFacade;

    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductAdminV1Dto.CreateResponse> createProduct(@Valid @RequestBody ProductAdminV1Dto.CreateRequest request) {
        ProductCreateInfo productCreateInfo = productFacade.createProduct(
            request.brandId(),
            request.name(),
            request.description(),
            request.price(),
            request.stock()
        );

        return ApiResponse.success(ProductAdminV1Dto.CreateResponse.from(productCreateInfo));
    }

    @Override
    @PutMapping("/{productId}")
    public ApiResponse<ProductAdminV1Dto.UpdateResponse> updateProduct(
        @PathVariable Long productId,
        @Valid @RequestBody ProductAdminV1Dto.UpdateRequest request
    ) {
        ProductUpdateInfo productUpdateInfo = productFacade.updateProduct(
            productId,
            request.name(),
            request.description(),
            request.price(),
            request.stock()
        );

        return ApiResponse.success(ProductAdminV1Dto.UpdateResponse.from(productUpdateInfo));
    }

    @Override
    @DeleteMapping("/{productId}")
    public ApiResponse<Void> deleteProduct(@PathVariable Long productId) {
        productFacade.deleteProduct(productId);

        return ApiResponse.success();
    }
}
