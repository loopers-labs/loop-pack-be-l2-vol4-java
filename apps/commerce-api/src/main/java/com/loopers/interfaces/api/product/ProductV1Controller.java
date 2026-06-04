package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductDetailInfo;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller {

    private final ProductFacade productFacade;

    @PostMapping
    public ApiResponse<ProductV1Dto.ProductResponse> createProduct(
        @RequestBody ProductV1Dto.CreateProductRequest request
    ) {
        ProductInfo info = productFacade.createProduct(
            request.brandId(),
            request.name(),
            request.description(),
            request.price(),
            request.stock()
        );
        ProductV1Dto.ProductResponse response = ProductV1Dto.ProductResponse.from(info);
        return ApiResponse.success(response);
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductV1Dto.ProductDisplayResponse> getProduct(
        @PathVariable(value = "productId") Long productId
    ) {
        ProductV1Dto.ProductDisplayResponse response = ProductV1Dto.ProductDisplayResponse.from(
            productFacade.getProductDisplay(productId)
        );
        return ApiResponse.success(response);
    }

    @GetMapping("/{productId}/detail")
    public ApiResponse<ProductV1Dto.ProductDetailResponse> getProductDetail(
        @PathVariable(value = "productId") Long productId
    ) {
        ProductDetailInfo info = productFacade.getProductDetail(productId);
        ProductV1Dto.ProductDetailResponse response = ProductV1Dto.ProductDetailResponse.from(info);
        return ApiResponse.success(response);
    }

    @GetMapping
    public ApiResponse<ProductV1Dto.ProductPageResponse> getAllProducts(
        @RequestParam(value = "brandId", required = false) Long brandId,
        @RequestParam(value = "sort", required = false) String sort,
        @RequestParam(value = "direction", required = false) String direction,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size
    ) {
        ProductV1Dto.ProductPageResponse response = ProductV1Dto.ProductPageResponse.from(
            productFacade.searchProducts(brandId, sort, direction, page, size)
        );
        return ApiResponse.success(response);
    }

    @PutMapping("/{productId}")
    public ApiResponse<ProductV1Dto.ProductResponse> updateProduct(
        @PathVariable(value = "productId") Long productId,
        @RequestBody ProductV1Dto.UpdateProductRequest request
    ) {
        ProductInfo info = productFacade.updateProduct(
            productId,
            request.brandId(),
            request.name(),
            request.description(),
            request.price(),
            request.stock()
        );
        ProductV1Dto.ProductResponse response = ProductV1Dto.ProductResponse.from(info);
        return ApiResponse.success(response);
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<Void> deleteProduct(
        @PathVariable(value = "productId") Long productId
    ) {
        productFacade.deleteProduct(productId);
        return ApiResponse.success(null);
    }
}
