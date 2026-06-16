package com.loopers.product.interfaces.api;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.product.application.ProductAdminService;
import com.loopers.product.application.ProductResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/admin/products")
public class ProductAdminV1Controller implements ProductAdminV1ApiSpec {

    private final ProductAdminService productAdminService;

    @PostMapping
    @Override
    public ApiResponse<ProductAdminV1Response.AdminDetail> create(
        @Valid @RequestBody ProductAdminV1Request.Create request
    ) {
        ProductResult.AdminDetail result = productAdminService.create(request.toCommand());
        return ApiResponse.success(ProductAdminV1Response.AdminDetail.from(result));
    }

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<ProductAdminV1Response.AdminDetail> get(@PathVariable Long productId) {
        return ApiResponse.success(ProductAdminV1Response.AdminDetail.from(productAdminService.getProduct(productId)));
    }

    @GetMapping
    @Override
    public ApiResponse<List<ProductAdminV1Response.AdminDetail>> getAll() {
        List<ProductAdminV1Response.AdminDetail> responses = productAdminService.getProducts().stream()
                .map(ProductAdminV1Response.AdminDetail::from)
                .toList();
        return ApiResponse.success(responses);
    }

    @PutMapping("/{productId}")
    @Override
    public ApiResponse<ProductAdminV1Response.AdminDetail> update(
        @PathVariable Long productId,
        @Valid @RequestBody ProductAdminV1Request.Update request
    ) {
        ProductResult.AdminDetail result = productAdminService.update(request.toCommand(productId));
        return ApiResponse.success(ProductAdminV1Response.AdminDetail.from(result));
    }

    @DeleteMapping("/{productId}")
    @Override
    public ApiResponse<Void> delete(@PathVariable Long productId) {
        productAdminService.delete(productId);
        return ApiResponse.success(null);
    }

    @PostMapping("/{productId}/suspend")
    @Override
    public ApiResponse<Void> suspend(@PathVariable Long productId) {
        productAdminService.suspend(productId);
        return ApiResponse.success(null);
    }

    @PostMapping("/{productId}/resume")
    @Override
    public ApiResponse<Void> resume(@PathVariable Long productId) {
        productAdminService.resume(productId);
        return ApiResponse.success(null);
    }
}
