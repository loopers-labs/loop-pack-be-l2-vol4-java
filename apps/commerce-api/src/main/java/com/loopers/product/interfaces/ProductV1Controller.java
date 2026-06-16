package com.loopers.product.interfaces;

import com.loopers.product.application.ProductFacade;
import com.loopers.product.application.ProductInfo;
import com.loopers.product.domain.SortCondition;
import com.loopers.support.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller {

    private final ProductFacade productFacade;

    @GetMapping
    public ApiResponse<List<ProductV1Dto.ProductResponse>> getProducts(
        @RequestParam(defaultValue = "latest") String sortBy,
        @RequestParam(required = false) Long brandId,
        @RequestParam(defaultValue = "false") boolean inStock,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        SortCondition sort = SortCondition.from(sortBy);
        List<ProductInfo> products = productFacade.getProducts(sort, brandId, inStock, page, size);
        return ApiResponse.success(products.stream().map(ProductV1Dto.ProductResponse::from).toList());
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductV1Dto.ProductResponse> getProduct(@PathVariable Long productId) {
        ProductInfo info = productFacade.getProduct(productId);
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(info));
    }
}
