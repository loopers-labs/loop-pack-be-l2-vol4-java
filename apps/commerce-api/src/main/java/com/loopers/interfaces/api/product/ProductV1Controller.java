package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductListResult;
import com.loopers.application.user.UserFacade;
import com.loopers.domain.product.ProductSortType;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller {

    private final ProductFacade productFacade;
    private final UserFacade userFacade;

    @PostMapping
    public ApiResponse<ProductV1Dto.ProductResponse> createProduct(
        @RequestBody ProductV1Dto.CreateProductRequest request
    ) {
        ProductInfo info = productFacade.createProduct(
            request.brandId(),
            request.name(),
            request.description(),
            request.imageUrl(),
            request.price(),
            request.stock()
        );
        ProductV1Dto.ProductResponse response = ProductV1Dto.ProductResponse.from(info);
        return ApiResponse.success(response);
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductV1Dto.ProductResponse> getProduct(
        @PathVariable(value = "productId") Long productId
    ) {
        ProductInfo info = productFacade.getProduct(productId);
        ProductV1Dto.ProductResponse response = ProductV1Dto.ProductResponse.from(info);
        return ApiResponse.success(response);
    }

    @GetMapping("/{productId}/detail")
    public ApiResponse<ProductV1Dto.ProductDetailResponse> getProductDetail(
        @PathVariable(value = "productId") Long productId,
        @RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
        @RequestHeader(value = "X-Loopers-LoginPw", required = false) String loginPw
    ) {
        // 식별된 User만 좋아요 여부를 본다. 헤더가 없으면 Guest로 간주(liked=false).
        Long userId = (loginId != null && loginPw != null) ? userFacade.authenticate(loginId, loginPw) : null;
        ProductV1Dto.ProductDetailResponse response =
            ProductV1Dto.ProductDetailResponse.from(productFacade.getProductDetail(productId, userId));
        return ApiResponse.success(response);
    }

    @GetMapping
    public ApiResponse<ProductV1Dto.ProductListPageResponse> getProducts(
        @RequestParam(value = "brandId", required = false) Long brandId,
        @RequestParam(value = "sort", defaultValue = "LATEST") ProductSortType sort,
        @RequestParam(value = "cursor", required = false) String cursor,
        @RequestParam(value = "size", defaultValue = "20") int size,
        @RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
        @RequestHeader(value = "X-Loopers-LoginPw", required = false) String loginPw
    ) {
        // 식별된 User만 각 상품의 좋아요 여부를 본다. 헤더 없으면 Guest(liked=false).
        Long userId = (loginId != null && loginPw != null) ? userFacade.authenticate(loginId, loginPw) : null;
        ProductListResult result = productFacade.getProducts(brandId, sort, cursor, size, userId);
        return ApiResponse.success(ProductV1Dto.ProductListPageResponse.from(result));
    }

    @PutMapping("/{productId}")
    public ApiResponse<ProductV1Dto.ProductResponse> updateProduct(
        @PathVariable(value = "productId") Long productId,
        @RequestBody ProductV1Dto.UpdateProductRequest request
    ) {
        ProductInfo info = productFacade.updateProduct(
            productId,
            request.name(),
            request.description(),
            request.imageUrl(),
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
