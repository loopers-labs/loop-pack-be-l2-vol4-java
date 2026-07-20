package com.loopers.application.catalog.product;

import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductStatus;

public record ProductResult(
    Long id,
    Long brandId,
    String brandName,
    String name,
    String description,
    Long price,
    Integer stockQuantity,
    Long likeCount,
    ProductStatus status,
    boolean liked,
    Long rank
) {
    public ProductResult(
        Long id,
        Long brandId,
        String brandName,
        String name,
        String description,
        Long price,
        Integer stockQuantity,
        Long likeCount,
        ProductStatus status,
        boolean liked
    ) {
        this(id, brandId, brandName, name, description, price, stockQuantity, likeCount, status, liked, null);
    }

    public static ProductResult from(Product product, Brand brand) {
        return from(product, brand, false);
    }

    public static ProductResult from(Product product, Brand brand, boolean liked) {
        return new ProductResult(
            product.getId(),
            product.getBrandId(),
            brand.getName(),
            product.getName(),
            product.getDescription(),
            product.getPriceAmount(),
            product.getStockQuantity(),
            product.getLikeCount(),
            product.getStatus(),
            liked,
            null
        );
    }

    public ProductResult withLiked(boolean liked) {
        return new ProductResult(
            id,
            brandId,
            brandName,
            name,
            description,
            price,
            stockQuantity,
            likeCount,
            status,
            liked,
            rank
        );
    }

    public ProductResult withRank(Long rank) {
        return new ProductResult(
            id,
            brandId,
            brandName,
            name,
            description,
            price,
            stockQuantity,
            likeCount,
            status,
            liked,
            rank
        );
    }
}
