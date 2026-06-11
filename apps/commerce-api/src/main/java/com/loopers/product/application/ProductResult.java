package com.loopers.product.application;

import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductDisplayStatus;

public class ProductResult {

    public record Detail(
        Long id,
        Long brandId,
        String brandName,
        String name,
        String description,
        long price,
        ProductDisplayStatus displayStatus,
        String thumbnailUrl,
        long likeCount
    ) {
        public static Detail from(Product product, String brandName, int stockQuantity, long likeCount) {
            return new Detail(
                product.getId(),
                product.getBrandId(),
                brandName,
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.displayStatus(stockQuantity),
                product.getThumbnailUrl(),
                likeCount
            );
        }
    }

    public record AdminDetail(
        Long id,
        Long brandId,
        String name,
        String description,
        long price,
        com.loopers.product.domain.ProductStatus status,
        String thumbnailUrl,
        int stockQuantity
    ) {
        public static AdminDetail from(Product product, int stockQuantity) {
            return new AdminDetail(
                product.getId(),
                product.getBrandId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStatus(),
                product.getThumbnailUrl(),
                stockQuantity
            );
        }
    }
}
