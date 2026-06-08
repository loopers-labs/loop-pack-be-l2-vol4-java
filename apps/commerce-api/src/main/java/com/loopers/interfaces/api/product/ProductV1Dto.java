package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;

import java.util.List;

public class ProductV1Dto {

    public record ProductResponse(
            Long id,
            String name,
            String status,
            Long brandId,
            String brandName,
            long likeCount
    ) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                    info.id(), info.name(), info.status(),
                    info.brandId(), info.brandName(), info.likeCount()
            );
        }

        public static List<ProductResponse> from(List<ProductInfo> infoList) {
            return infoList.stream().map(ProductResponse::from).toList();
        }
    }
}