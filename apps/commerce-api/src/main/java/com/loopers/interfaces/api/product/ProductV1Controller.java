package com.loopers.interfaces.api.product;

import com.loopers.application.like.ProductLikeFacade;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.AuthenticatedUser;
import com.loopers.interfaces.auth.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller {

    private final ProductFacade productFacade;
    private final ProductLikeFacade productLikeFacade;

    @PostMapping
    public ApiResponse<ProductV1Dto.ProductResponse> createProduct(
        @Valid @RequestBody ProductV1Dto.CreateProductRequest request
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
    public ApiResponse<ProductV1Dto.ProductResponse> getProduct(
        @PathVariable(value = "productId") Long productId
    ) {
        ProductInfo info = productFacade.getProduct(productId);
        ProductV1Dto.ProductResponse response = ProductV1Dto.ProductResponse.from(info);
        return ApiResponse.success(response);
    }

    @GetMapping
    public ApiResponse<List<ProductV1Dto.ProductResponse>> getAllProducts(
        @RequestParam(value = "sort", required = false) String sort
    ) {
        List<ProductInfo> infos = productFacade.getAllProducts(sort);
        List<ProductV1Dto.ProductResponse> responses = infos.stream()
            .map(ProductV1Dto.ProductResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }

    @PutMapping("/{productId}")
    public ApiResponse<ProductV1Dto.ProductResponse> updateProduct(
        @PathVariable(value = "productId") Long productId,
        @Valid @RequestBody ProductV1Dto.UpdateProductRequest request
    ) {
        ProductInfo info = productFacade.updateProduct(
            productId,
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

    @PostMapping("/{productId}/likes")
    public ApiResponse<Void> likeProduct(
        @LoginUser AuthenticatedUser user,
        @PathVariable(value = "productId") Long productId
    ) {
        productLikeFacade.likeProduct(user.loginId(), productId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{productId}/likes")
    public ApiResponse<Void> unlikeProduct(
        @LoginUser AuthenticatedUser user,
        @PathVariable(value = "productId") Long productId
    ) {
        productLikeFacade.unlikeProduct(user.loginId(), productId);
        return ApiResponse.success(null);
    }
}
