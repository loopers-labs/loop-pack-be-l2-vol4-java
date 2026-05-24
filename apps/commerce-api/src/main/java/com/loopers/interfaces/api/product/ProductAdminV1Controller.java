package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductAdminFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/products")
public class ProductAdminV1Controller {

    private final ProductAdminFacade productAdminFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductAdminV1Dto.ProductResponse> createProduct(@RequestBody ProductAdminV1Dto.CreateProductRequest request) {
        ProductInfo info = productAdminFacade.createProduct(request.toCommand());
        return ApiResponse.success(ProductAdminV1Dto.ProductResponse.from(info));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductAdminV1Dto.ProductResponse> getProduct(@PathVariable Long productId) {
        ProductInfo info = productAdminFacade.getProduct(productId);
        return ApiResponse.success(ProductAdminV1Dto.ProductResponse.from(info));
    }
}
