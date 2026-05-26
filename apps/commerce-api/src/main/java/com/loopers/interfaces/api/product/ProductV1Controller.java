package com.loopers.interfaces.api.product;

import com.loopers.application.like.ProductLikeFacade;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.AuthenticatedUser;
import com.loopers.interfaces.auth.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller {

    private final ProductFacade productFacade;
    private final ProductLikeFacade productLikeFacade;

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
        @RequestParam(value = "brandId", required = false) Long brandId,
        @RequestParam(value = "sort", required = false) String sort,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size
    ) {
        List<ProductInfo> infos = productFacade.getAllProducts(brandId, sort, page, size);
        List<ProductV1Dto.ProductResponse> responses = infos.stream()
            .map(ProductV1Dto.ProductResponse::from)
            .toList();
        return ApiResponse.success(responses);
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
