package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;

public record ProductDetailInfo(
    Long id,
    Long brandId,
    String brandName,
    String name,
    String description,
    Long price,
    Integer stock,
    long likeCount,
    Integer rank // 오늘 랭킹 1-based 순위. 순위 밖/장애 시 null. 캐시에는 항상 null 로 저장된다.
) {
    public static ProductDetailInfo from(Product product, Brand brand, long likeCount) {
        return new ProductDetailInfo(
            product.getId(),
            product.getBrandId(),
            brand.getName(),
            product.getName(),
            product.getDescription(),
            product.getPrice(),
            product.getStock(),
            likeCount,
            null
        );
    }

    /** 캐시 조회 이후 실시간 순위를 덧씌운다 — 순위를 캐시에 얼리지 않기 위한 복사 생성. */
    public ProductDetailInfo withRank(Integer rank) {
        return new ProductDetailInfo(id, brandId, brandName, name, description, price, stock, likeCount, rank);
    }
}
