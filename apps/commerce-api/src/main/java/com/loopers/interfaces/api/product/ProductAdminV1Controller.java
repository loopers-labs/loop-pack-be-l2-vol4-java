package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductApplicationService;
import com.loopers.application.product.ProductInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/products")
public class ProductAdminV1Controller implements ProductAdminV1ApiSpec {

    private final ProductApplicationService productApplicationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductV1Dto.CreateProductResponse> createProduct(
            @RequestBody ProductV1Dto.CreateProductRequest request
    ) {
        ProductInfo info = productApplicationService.createProduct(
                request.brandId(),
                request.name(),
                request.description(),
                request.price(),
                request.quantity()
        );
        return ApiResponse.success(new ProductV1Dto.CreateProductResponse(info.id()));
    }

    @GetMapping
    public ApiResponse<PageResult<ProductV1Dto.AdminPlpResponse>> getAllProducts(
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size
    ) {
        return ApiResponse.success(
                PageResult.from(
                        productApplicationService.getAllProducts(brandId, PageRequest.of(page, size))
                                .map(ProductV1Dto.AdminPlpResponse::from)
                )
        );
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductV1Dto.AdminPdpResponse> getProduct(
            @PathVariable Long productId
    ) {
        return ApiResponse.success(ProductV1Dto.AdminPdpResponse.from(productApplicationService.getProduct(productId)));
    }

    @PutMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateProduct(
            @PathVariable Long productId,
            @RequestBody ProductV1Dto.UpdateProductRequest request
    ) {
        productApplicationService.updateProduct(
                productId,
                request.name(),
                request.description(),
                request.price(),
                request.quantity()
        );
    }

    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(
            @PathVariable Long productId
    ) {
        productApplicationService.deleteProduct(productId);
    }
}
