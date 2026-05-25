package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductAdminInfo;
import org.springframework.data.domain.Page;

import java.time.ZonedDateTime;
import java.util.List;

public class ProductAdminV1Dto {

    public record BrandSummary(Long id, String name) {}

    public record Response(
        Long id,
        String name,
        String description,
        Long price,
        BrandSummary brand,
        Long likeCount,
        Integer stockQuantity,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
    ) {
        public static Response from(ProductAdminInfo info) {
            return new Response(
                info.id(),
                info.name(),
                info.description(),
                info.price(),
                new BrandSummary(info.brandId(), info.brandName()),
                info.likeCount(),
                info.stockQuantity(),
                info.createdAt(),
                info.updatedAt()
            );
        }
    }

    public record PageResponse(
        List<Response> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        public static PageResponse from(Page<ProductAdminInfo> page) {
            return new PageResponse(
                page.getContent().stream().map(Response::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
            );
        }
    }
}
