package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductCommand;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductService;
import com.loopers.domain.product.ProductSort;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/products")
public class ProductAdminV1Controller {

    private final ProductFacade productFacade;
    private final ProductService productService;

    @GetMapping
    public ApiResponse<Page<ProductAdminV1Dto.ProductResponse>> getProducts(
        @RequestParam(required = false) Long brandId,
        @RequestParam(defaultValue = "LATEST") ProductSort sort,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<ProductInfo> products = productFacade.getProducts(brandId, sort, PageRequest.of(page, size));
        return ApiResponse.success(products.map(ProductAdminV1Dto.ProductResponse::from));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductAdminV1Dto.ProductResponse> getProduct(
        @PathVariable Long productId
    ) {
        ProductInfo info = productFacade.getProductWithStock(productId);
        return ApiResponse.success(ProductAdminV1Dto.ProductResponse.from(info));
    }

    @PostMapping
    public ApiResponse<ProductAdminV1Dto.ProductResponse> createProduct(
        @RequestBody @Valid ProductAdminV1Dto.ProductCreateRequest request
    ) {
        ProductInfo info = productFacade.createProduct(
            new ProductCommand.Create(request.brandId(), request.name(), request.price(), request.stock())
        );
        return ApiResponse.success(ProductAdminV1Dto.ProductResponse.from(info));
    }

    @PutMapping("/{productId}")
    public ApiResponse<ProductAdminV1Dto.ProductResponse> updateProduct(
        @PathVariable Long productId,
        @RequestBody @Valid ProductAdminV1Dto.ProductUpdateRequest request
    ) {
        ProductInfo info = productFacade.updateProduct(
            productId,
            new ProductCommand.Update(request.brandId(), request.name(), request.price(), request.stock())
        );
        return ApiResponse.success(ProductAdminV1Dto.ProductResponse.from(info));
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<Void> deleteProduct(
        @PathVariable Long productId
    ) {
        productService.deleteProduct(productId);
        return ApiResponse.success(null);
    }
}
