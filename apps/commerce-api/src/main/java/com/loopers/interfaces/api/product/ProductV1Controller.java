package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/products")
public class ProductV1Controller {

    private final ProductFacade productFacade;

    @GetMapping
    public ApiResponse<Page<ProductV1Dto.ProductAdminResponse>> getProducts(
        @RequestParam(required = false) Long brandId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<ProductInfo> products = productFacade.getProducts(brandId, "latest", page, size);
        return ApiResponse.success(products.map(ProductV1Dto.ProductAdminResponse::from));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductV1Dto.ProductAdminResponse> getProduct(
        @PathVariable Long productId
    ) {
        ProductInfo info = productFacade.getProduct(productId);
        return ApiResponse.success(ProductV1Dto.ProductAdminResponse.from(info));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductV1Dto.ProductResponse> register(
        @RequestBody ProductV1Dto.RegisterRequest request
    ) {
        ProductInfo info = productFacade.createProduct(
            request.brandId(),
            request.name(),
            request.description(),
            request.price(),
            request.initialQuantity()
        );
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(info));
    }
}
