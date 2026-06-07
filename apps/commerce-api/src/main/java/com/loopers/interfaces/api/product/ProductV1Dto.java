package com.loopers.interfaces.api.product;

import java.util.List;

import org.springframework.data.domain.Page;

import com.loopers.application.product.ProductDetailInfo;
import com.loopers.application.product.ProductSummaryInfo;

public class ProductV1Dto {

    public record BrandResponse(Long brandId, String name) {
    }

    public record DetailResponse(
        Long productId,
        String name,
        String description,
        BrandResponse brand,
        Integer price,
        Boolean isAvailable,
        Integer likeCount
    ) {

        public static DetailResponse from(ProductDetailInfo productDetailInfo) {
            return new DetailResponse(
                productDetailInfo.productId(),
                productDetailInfo.name(),
                productDetailInfo.description(),
                new BrandResponse(productDetailInfo.brandId(), productDetailInfo.brandName()),
                productDetailInfo.price(),
                productDetailInfo.isAvailable(),
                productDetailInfo.likeCount()
            );
        }
    }

    public record SummaryResponse(
        Long productId,
        String name,
        BrandResponse brand,
        Integer price,
        Boolean isAvailable,
        Integer likeCount
    ) {

        public static SummaryResponse from(ProductSummaryInfo productSummaryInfo) {
            return new SummaryResponse(
                productSummaryInfo.productId(),
                productSummaryInfo.name(),
                new BrandResponse(productSummaryInfo.brandId(), productSummaryInfo.brandName()),
                productSummaryInfo.price(),
                productSummaryInfo.isAvailable(),
                productSummaryInfo.likeCount()
            );
        }
    }

    public record PageResponse(
        List<SummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {

        public static PageResponse from(Page<ProductSummaryInfo> productsSummaryInfo) {
            List<SummaryResponse> content = productsSummaryInfo.getContent()
                .stream()
                .map(SummaryResponse::from)
                .toList();

            return new PageResponse(
                content,
                productsSummaryInfo.getNumber(),
                productsSummaryInfo.getSize(),
                productsSummaryInfo.getTotalElements(),
                productsSummaryInfo.getTotalPages()
            );
        }
    }
}
