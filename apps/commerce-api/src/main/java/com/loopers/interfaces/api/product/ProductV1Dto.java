package com.loopers.interfaces.api.product;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.loopers.application.product.ProductDetailInfo;
import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductPageInfo;
import java.util.List;

public class ProductV1Dto {
  public record CreateProductRequest(
      String name, String description, Long price, Integer stock, Long brandId) {}

  public record UpdateProductRequest(
      String name, String description, Long price, Integer stock, Long brandId) {}

  public record ProductResponse(
      Long id,
      String name,
      String description,
      Long price,
      Integer stock,
      Long brandId,
      Long likeCount) {
    public static ProductResponse from(ProductInfo info) {
      return new ProductResponse(
          info.id(),
          info.name(),
          info.description(),
          info.price(),
          info.stock(),
          info.brandId(),
          info.likeCount());
    }
  }

  public record ProductDetailResponse(
      Long id,
      String name,
      String description,
      Long price,
      Integer stock,
      Long brandId,
      Long likeCount,
      @JsonInclude(JsonInclude.Include.ALWAYS) Long rank) {
    public static ProductDetailResponse from(ProductDetailInfo info) {
      ProductInfo product = info.product();
      return new ProductDetailResponse(
          product.id(),
          product.name(),
          product.description(),
          product.price(),
          product.stock(),
          product.brandId(),
          product.likeCount(),
          info.rank());
    }
  }

  public record ProductListResponse(
      List<ProductResponse> items, int page, int size, long totalCount, int totalPages) {
    public static ProductListResponse from(ProductPageInfo pageInfo) {
      return new ProductListResponse(
          pageInfo.items().stream().map(ProductResponse::from).toList(),
          pageInfo.page(),
          pageInfo.size(),
          pageInfo.totalCount(),
          pageInfo.totalPages());
    }
  }
}
