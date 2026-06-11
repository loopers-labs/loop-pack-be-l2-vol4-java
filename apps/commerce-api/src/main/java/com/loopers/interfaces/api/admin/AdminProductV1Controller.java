package com.loopers.interfaces.api.admin;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.product.ProductService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.product.ProductDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/products")
public class AdminProductV1Controller {

    private final ProductFacade productFacade;
    private final ProductService productService;

    @GetMapping
    public ApiResponse<List<ProductDto.List.V1.Response>> getProducts(
        @RequestParam(value = "brandId", required = false) Long brandId,
        @RequestParam(value = "sort", required = false) String sort,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size
    ) {
        List<ProductDto.List.V1.Response> responses = productFacade.getAllProducts(brandId, sort, page, size).stream()
            .map(ProductDto.List.V1.Response::from)
            .toList();
        return ApiResponse.success(responses);
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductDto.Get.V1.Response> getProduct(
        @PathVariable(value = "productId") Long productId
    ) {
        ProductInfo info = productFacade.getProduct(productId);
        return ApiResponse.success(ProductDto.Get.V1.Response.from(info));
    }

    @PostMapping
    public ApiResponse<ProductDto.Create.V1.Response> createProduct(
        @Valid @RequestBody ProductDto.Create.V1.Request request
    ) {
        ProductInfo info = productFacade.createProduct(
            request.brandId(),
            request.name(),
            request.description(),
            request.price(),
            request.stock()
        );
        return ApiResponse.success(ProductDto.Create.V1.Response.from(info));
    }

    @PutMapping("/{productId}")
    public ApiResponse<ProductDto.Update.V1.Response> updateProduct(
        @PathVariable(value = "productId") Long productId,
        @Valid @RequestBody ProductDto.Update.V1.Request request
    ) {
        ProductInfo info = productFacade.updateProduct(
            productId,
            request.name(),
            request.description(),
            request.price(),
            request.stock()
        );
        return ApiResponse.success(ProductDto.Update.V1.Response.from(info));
    }

    @DeleteMapping("/{productId}")
    public ApiResponse<Void> deleteProduct(
        @PathVariable(value = "productId") Long productId
    ) {
        productService.deleteProduct(productId);
        return ApiResponse.success(null);
    }
}
