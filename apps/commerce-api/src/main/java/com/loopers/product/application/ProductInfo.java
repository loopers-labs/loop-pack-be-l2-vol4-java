package com.loopers.product.application;

import com.loopers.product.domain.ProductModel;

public record ProductInfo(Long id, String name, String description, Long price, Integer stock, Long brandId, String brandName, Long likeCount) {

    /** 브랜드명 조합이 불필요한 경우 (등록·수정 등 admin 흐름) */
    public static ProductInfo from(ProductModel model) {
        return new ProductInfo(
            model.getId(),
            model.getName(),
            model.getDescription(),
            model.getPrice(),
            model.getStock(),
            model.getBrandId(),
            null,
            model.getLikeCount()
        );
    }

    /** 브랜드명을 함께 제공하는 경우 (상품 상세·목록 조회) */
    public static ProductInfo from(ProductModel model, String brandName) {
        return new ProductInfo(
            model.getId(),
            model.getName(),
            model.getDescription(),
            model.getPrice(),
            model.getStock(),
            model.getBrandId(),
            brandName,
            model.getLikeCount()
        );
    }
}
