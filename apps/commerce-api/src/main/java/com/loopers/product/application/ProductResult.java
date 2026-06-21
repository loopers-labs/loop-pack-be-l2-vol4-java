package com.loopers.product.application;

import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductDisplayStatus;

import java.util.List;

public class ProductResult {

    public record Page(
        List<Detail> content,
        long totalCount,
        int page,
        int size
    ) {
    }

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
        public static Detail from(Product product, String brandName, int stockQuantity) {
            return new Detail(
                product.getId(),
                product.getBrandId(),
                brandName,
                product.getName(),
                product.getDescription(),
                product.getPrice().value(),
                product.displayStatus(stockQuantity),
                product.getThumbnailUrl(),
                product.getLikeCount()
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
                product.getPrice().value(),
                product.getStatus(),
                product.getThumbnailUrl(),
                stockQuantity
            );
        }
    }
}
