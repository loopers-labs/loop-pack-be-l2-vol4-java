package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductAdminInfo;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.user.AdminAuth;
import com.loopers.interfaces.api.user.AuthHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/products")
public class ProductAdminV1Controller {

    private final ProductFacade productFacade;

    @GetMapping
    public ApiResponse<PageResponse<ProductAdminV1Dto.ProductResponse>> getProducts(
        @RequestHeader(AuthHeaders.LDAP) String ldap,
        @RequestParam Long brandId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        AdminAuth.validate(ldap);
        Page<ProductInfo> infos = productFacade.getProductsByBrandId(brandId, PageRequest.of(page, size));
        return ApiResponse.success(PageResponse.from(infos.map(ProductAdminV1Dto.ProductResponse::from)));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductAdminV1Dto.ProductDetailResponse> getProduct(
        @RequestHeader(AuthHeaders.LDAP) String ldap,
        @PathVariable Long productId
    ) {
        AdminAuth.validate(ldap);
        ProductAdminInfo info = productFacade.getProductForAdmin(productId);
        return ApiResponse.success(ProductAdminV1Dto.ProductDetailResponse.from(info));
    }

    @PostMapping
    public ApiResponse<ProductAdminV1Dto.ProductDetailResponse> createProduct(
        @RequestHeader(AuthHeaders.LDAP) String ldap,
        @RequestBody ProductAdminV1Dto.CreateProductRequest request
    ) {
        AdminAuth.validate(ldap);
        ProductAdminInfo info = productFacade.createProductForAdmin(
            request.brandId(),
            request.name(),
            request.price(),
            request.stock()
        );
        return ApiResponse.success(ProductAdminV1Dto.ProductDetailResponse.from(info));
    }

    @PutMapping("/{productId}")
    public ApiResponse<ProductAdminV1Dto.ProductDetailResponse> updateProduct(
        @RequestHeader(AuthHeaders.LDAP) String ldap,
        @PathVariable Long productId,
        @RequestBody ProductAdminV1Dto.UpdateProductRequest request
    ) {
        AdminAuth.validate(ldap);
        ProductAdminInfo info = productFacade.updateProductForAdmin(
            productId,
            request.name(),
            request.price(),
            request.stock()
        );
        return ApiResponse.success(ProductAdminV1Dto.ProductDetailResponse.from(info));
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<Void> deleteProduct(
        @RequestHeader(AuthHeaders.LDAP) String ldap,
        @PathVariable Long productId
    ) {
        AdminAuth.validate(ldap);
        productFacade.deleteProduct(productId);
        return ApiResponse.success(null);
    }
}
