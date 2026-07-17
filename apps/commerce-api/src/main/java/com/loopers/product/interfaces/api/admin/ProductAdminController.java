package com.loopers.product.interfaces.api.admin;

import com.loopers.common.interfaces.api.AdminAuth;
import com.loopers.common.interfaces.api.ApiResponse;
import com.loopers.common.interfaces.api.PagedResponse;
import com.loopers.product.application.ProductFacade;
import com.loopers.product.application.ProductInfo;
import com.loopers.product.interfaces.api.CreateProductRequest;
import com.loopers.product.interfaces.api.ProductResponse;
import com.loopers.product.interfaces.api.UpdateProductRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/products")
public class ProductAdminController {

    private final ProductFacade productFacade;

    @GetMapping
    public ApiResponse<PagedResponse<ProductResponse>> getProducts(
        @RequestHeader(AdminAuth.LDAP_HEADER) String ldap,
        @RequestParam(value = "brandId", required = false) Long brandId,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "20") int size) {
        AdminAuth.verify(ldap);
        Page<ProductInfo> result = productFacade.getProductsForAdmin(brandId, page, size);
        return ApiResponse.success(
            PagedResponse.from(result.map(ProductResponse::from)));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductResponse> getProduct(
        @RequestHeader(AdminAuth.LDAP_HEADER) String ldap,
        @PathVariable("productId") Long productId) {
        AdminAuth.verify(ldap);
        return ApiResponse.success(
            ProductResponse.from(productFacade.getProductForAdmin(productId)));
    }

    @PostMapping
    public ApiResponse<ProductResponse> createProduct(
        @RequestHeader(AdminAuth.LDAP_HEADER) String ldap,
        @RequestBody CreateProductRequest request) {
        AdminAuth.verify(ldap);
        ProductInfo info =
            productFacade.createProduct(
                request.brandId(),
                request.name(),
                request.description(),
                request.price(),
                request.stock());
        return ApiResponse.success(ProductResponse.from(info));
    }

    @PutMapping("/{productId}")
    public ApiResponse<ProductResponse> updateProduct(
        @RequestHeader(AdminAuth.LDAP_HEADER) String ldap,
        @PathVariable("productId") Long productId,
        @RequestBody UpdateProductRequest request) {
        AdminAuth.verify(ldap);
        ProductInfo info =
            productFacade.updateProduct(
                productId, request.name(), request.description(), request.price(), request.stock());
        return ApiResponse.success(ProductResponse.from(info));
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<Void> deleteProduct(
        @RequestHeader(AdminAuth.LDAP_HEADER) String ldap,
        @PathVariable("productId") Long productId) {
        AdminAuth.verify(ldap);
        productFacade.deleteProduct(productId);
        return ApiResponse.success(null);
    }
}
