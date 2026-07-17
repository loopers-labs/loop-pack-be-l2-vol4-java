package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductDetailFacade;
import com.loopers.application.product.ProductDetailInfo;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductPageInfo;
import com.loopers.domain.product.ProductSearchCondition;
import com.loopers.domain.product.ProductSortType;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller {

  private final ProductFacade productFacade;
  private final ProductDetailFacade productDetailFacade;

  @PostMapping
  public ApiResponse<ProductV1Dto.ProductResponse> createProduct(
      @RequestBody ProductV1Dto.CreateProductRequest request) {
    ProductInfo info =
        productFacade.createProduct(
            request.name(),
            request.description(),
            request.price(),
            request.stock(),
            request.brandId());
    ProductV1Dto.ProductResponse response = ProductV1Dto.ProductResponse.from(info);
    return ApiResponse.success(response);
  }

  @GetMapping("/{productId}")
  public ApiResponse<ProductV1Dto.ProductDetailResponse> getProduct(
      @PathVariable(value = "productId") Long productId) {
    ProductDetailInfo info = productDetailFacade.getProduct(productId);
    ProductV1Dto.ProductDetailResponse response = ProductV1Dto.ProductDetailResponse.from(info);
    return ApiResponse.success(response);
  }

  @GetMapping
  public ApiResponse<ProductV1Dto.ProductListResponse> getProducts(
      @RequestParam(value = "brandId", required = false) Long brandId,
      @RequestParam(value = "sort", required = false) String sort,
      @RequestParam(value = "page", defaultValue = "0") int page,
      @RequestParam(value = "size", defaultValue = "20") int size) {
    ProductSearchCondition condition =
        new ProductSearchCondition(brandId, ProductSortType.from(sort), page, size);
    ProductPageInfo pageInfo = productFacade.searchProducts(condition);
    return ApiResponse.success(ProductV1Dto.ProductListResponse.from(pageInfo));
  }

  @PutMapping("/{productId}")
  public ApiResponse<ProductV1Dto.ProductResponse> updateProduct(
      @PathVariable(value = "productId") Long productId,
      @RequestBody ProductV1Dto.UpdateProductRequest request) {
    ProductInfo info =
        productFacade.updateProduct(
            productId,
            request.name(),
            request.description(),
            request.price(),
            request.stock(),
            request.brandId());
    ProductV1Dto.ProductResponse response = ProductV1Dto.ProductResponse.from(info);
    return ApiResponse.success(response);
  }

  @DeleteMapping("/{productId}")
  public ApiResponse<Void> deleteProduct(@PathVariable(value = "productId") Long productId) {
    productFacade.deleteProduct(productId);
    return ApiResponse.success(null);
  }
}
