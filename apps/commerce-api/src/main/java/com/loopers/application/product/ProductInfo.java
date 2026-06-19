package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductStockModel;

import java.util.List;

public record ProductInfo(
        Long id,
        String name,
        String status,
        Long brandId,
        String brandName,
        Long minPrice,
        long likeCount,
        List<StockInfo> stocks
) {
    public record StockInfo(Long id, Long price, Integer quantity) {
        public static StockInfo from(ProductStockModel stock) {
            return new StockInfo(stock.getId(), stock.getPrice().getValue(), stock.getStockQuantity().getValue());
        }
    }

    public static ProductInfo from(ProductModel product, BrandModel brand, long likeCount) {
        return new ProductInfo(
                product.getId(),
                product.getName(),
                product.getStatus().getDescription(),
                product.getBrandId(),
                brand.getName(),
                product.getMinPrice(),
                likeCount,
                List.of()
        );
    }

    public static ProductInfo from(ProductModel product, BrandModel brand, long likeCount, List<ProductStockModel> stocks) {
        return new ProductInfo(
                product.getId(),
                product.getName(),
                product.getStatus().getDescription(),
                product.getBrandId(),
                brand.getName(),
                product.getMinPrice(),
                likeCount,
                stocks.stream().map(StockInfo::from).toList()
        );
    }
}
