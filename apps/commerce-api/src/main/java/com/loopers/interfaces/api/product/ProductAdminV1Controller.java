package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductApplicationService;
import com.loopers.application.product.ProductCriteria;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.common.PageResult;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/products")
public class ProductAdminV1Controller implements ProductAdminV1ApiSpec {

    private final ProductApplicationService productApplicationService;

    @GetMapping
    @Override
    public ApiResponse<ProductV1Dto.PageResponse> getProducts(
            @RequestHeader("X-Loopers-Ldap") String adminLdap,
            @RequestParam(value = "brandId", required = false) Long brandId,
            @RequestParam(value = "sort", defaultValue = "latest") String sort,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        PageResult<ProductInfo.ListItem> result = productApplicationService.getAllProducts(
                new ProductCriteria.GetAll(page, size, brandId, sort));
        return ApiResponse.success(ProductV1Dto.PageResponse.from(result));
    }

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<ProductV1Dto.DetailResponse> getProduct(
            @RequestHeader("X-Loopers-Ldap") String adminLdap,
            @PathVariable("productId") Long productId
    ) {
        ProductInfo.Detail info = productApplicationService.getProduct(productId);
        return ApiResponse.success(ProductV1Dto.DetailResponse.from(info));
    }

    @PostMapping
    @Override
    public ApiResponse<ProductV1Dto.CreatedResponse> register(
            @RequestHeader("X-Loopers-Ldap") String adminLdap,
            @RequestBody ProductV1Dto.RegisterRequest request
    ) {
        ProductInfo.Created info = productApplicationService.register(
                new ProductCriteria.Register(request.brandId(), request.name(), request.price(), request.stock()));
        return ApiResponse.success(ProductV1Dto.CreatedResponse.from(info));
    }

    @PutMapping("/{productId}")
    @Override
    public ApiResponse<Void> modify(
            @RequestHeader("X-Loopers-Ldap") String adminLdap,
            @PathVariable("productId") Long productId,
            @RequestBody ProductV1Dto.ModifyRequest request
    ) {
        productApplicationService.modify(
                new ProductCriteria.Modify(productId, request.name(), request.price(), request.stock()));
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{productId}")
    @Override
    public ApiResponse<Void> delete(
            @RequestHeader("X-Loopers-Ldap") String adminLdap,
            @PathVariable("productId") Long productId
    ) {
        productApplicationService.delete(productId);
        return ApiResponse.success(null);
    }
}
