package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/products")
public class ProductAdminV1Controller {

    private final ProductFacade productFacade;

    @GetMapping
    public ApiResponse<List<ProductAdminV1Dto.ProductResponse>> getProducts(
        @RequestHeader("X-Loopers-Ldap") String ldap,
        @RequestParam(required = false) Long brandId,
        @RequestParam(required = false, defaultValue = "latest") String sort,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        List<ProductInfo> infos = productFacade.getProductsForAdmin(brandId, sort, page, size);
        List<ProductAdminV1Dto.ProductResponse> responses = infos.stream()
            .map(ProductAdminV1Dto.ProductResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductAdminV1Dto.ProductResponse> getProduct(
        @RequestHeader("X-Loopers-Ldap") String ldap,
        @PathVariable Long productId
    ) {
        ProductInfo info = productFacade.getProductForAdmin(productId);
        return ApiResponse.success(ProductAdminV1Dto.ProductResponse.from(info));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductAdminV1Dto.ProductResponse> createProduct(
        @RequestHeader("X-Loopers-Ldap") String ldap,
        @RequestBody ProductAdminV1Dto.CreateProductRequest request
    ) {
        ProductInfo info = productFacade.createProduct(
            request.brandId(),
            request.name(),
            request.description(),
            request.price(),
            request.stock()
        );
        return ApiResponse.success(ProductAdminV1Dto.ProductResponse.from(info));
    }

    @PutMapping("/{productId}")
    public ApiResponse<ProductAdminV1Dto.ProductResponse> updateProduct(
        @RequestHeader("X-Loopers-Ldap") String ldap,
        @PathVariable Long productId,
        @RequestBody ProductAdminV1Dto.UpdateProductRequest request
    ) {
        ProductInfo info = productFacade.updateProduct(
            productId,
            request.name(),
            request.description(),
            request.price(),
            request.stock()
        );
        return ApiResponse.success(ProductAdminV1Dto.ProductResponse.from(info));
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<Void> deleteProduct(
        @RequestHeader("X-Loopers-Ldap") String ldap,
        @PathVariable Long productId
    ) {
        productFacade.deleteProduct(productId);
        return ApiResponse.success(null);
    }
}
