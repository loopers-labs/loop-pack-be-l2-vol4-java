package com.loopers.product.interfaces.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.loopers.product.application.ProductDetailInfo;
import com.loopers.product.application.RankedProductDetailInfo;

public record RankedProductDetailResponse(
        Long id,
        Long brandId,
        String brandName,
        String name,
        String description,
        Long price,
        Integer stock,
        long likeCount,
        @JsonInclude(JsonInclude.Include.ALWAYS) Long rank) {

    public static RankedProductDetailResponse from(RankedProductDetailInfo info) {
        ProductDetailInfo product = info.product();
        return new RankedProductDetailResponse(
                product.id(),
                product.brandId(),
                product.brandName(),
                product.name(),
                product.description(),
                product.price(),
                product.stock(),
                product.likeCount(),
                info.rank());
    }
}
