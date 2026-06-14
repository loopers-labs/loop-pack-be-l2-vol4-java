package com.loopers.product.interfaces.api;

import com.loopers.product.application.ProductAdminFacade;
import com.loopers.product.application.ProductInfo;
import com.loopers.shared.presentation.ApiResponse;
import com.loopers.shared.presentation.PageResponse;
import com.loopers.shared.pagination.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
public class ProductAdminV1Controller {

    private final ProductAdminFacade productAdminFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductAdminV1Dto.ProductResponse> createProduct(@Valid @RequestBody ProductAdminV1Dto.CreateProductRequest request) {
        ProductInfo info = productAdminFacade.createProduct(request.toCommand());
        return ApiResponse.success(ProductAdminV1Dto.ProductResponse.from(info));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductAdminV1Dto.ProductResponse> getProduct(@PathVariable Long productId) {
        ProductInfo info = productAdminFacade.getProduct(productId);
        return ApiResponse.success(ProductAdminV1Dto.ProductResponse.from(info));
    }

    @GetMapping
    public ApiResponse<PageResponse<ProductAdminV1Dto.ProductResponse>> getProducts(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) Long brandId
    ) {
        PageResult<ProductAdminV1Dto.ProductResponse> products = productAdminFacade.getProducts(page, size, brandId)
            .map(ProductAdminV1Dto.ProductResponse::from);
        return ApiResponse.success(PageResponse.from(products));
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<Object> deleteProduct(@PathVariable Long productId) {
        productAdminFacade.deleteProduct(productId);
        return ApiResponse.success();
    }

    @PutMapping("/{productId}")
    public ApiResponse<ProductAdminV1Dto.ProductResponse> updateProduct(
        @PathVariable Long productId,
        @Valid @RequestBody ProductAdminV1Dto.UpdateProductRequest request
    ) {
        ProductInfo info = productAdminFacade.updateProduct(request.toCommand(productId));
        return ApiResponse.success(ProductAdminV1Dto.ProductResponse.from(info));
    }
}
