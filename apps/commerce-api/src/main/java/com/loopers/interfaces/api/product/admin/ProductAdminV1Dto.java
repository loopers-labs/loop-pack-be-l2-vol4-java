package com.loopers.interfaces.api.product.admin;

import com.loopers.application.product.ProductAdminInfo;
import com.loopers.application.product.ProductInfo;
import org.springframework.data.domain.Page;

import java.time.ZonedDateTime;
import java.util.List;

public class ProductAdminV1Dto {

    public record CreateRequest(
        String name,
        String description,
        Long price,
        Integer stock,
        Long brandId
    ) {}

    public record UpdateRequest(
        String name,
        String description,
        Long price,
        Integer stock
    ) {}

    public record BrandResponse(Long id, String name) {
        public static BrandResponse from(ProductInfo.BrandSummary summary) {
            return new BrandResponse(summary.id(), summary.name());
        }
    }

    public record AdminProductSummary(
        Long id,
        String name,
        Long price,
        int stockQuantity,
        long likeCount,
        BrandResponse brand,
        ZonedDateTime deletedAt
    ) {
        public static AdminProductSummary from(ProductAdminInfo info) {
            return new AdminProductSummary(
                info.id(),
                info.name(),
                info.price(),
                info.stockQuantity(),
                info.likeCount(),
                BrandResponse.from(info.brand()),
                info.deletedAt()
            );
        }
    }

    public record AdminProductDetail(
        Long id,
        String name,
        String description,
        Long price,
        int stockQuantity,
        long likeCount,
        BrandResponse brand,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt,
        ZonedDateTime deletedAt
    ) {
        public static AdminProductDetail from(ProductAdminInfo info) {
            return new AdminProductDetail(
                info.id(),
                info.name(),
                info.description(),
                info.price(),
                info.stockQuantity(),
                info.likeCount(),
                BrandResponse.from(info.brand()),
                info.createdAt(),
                info.updatedAt(),
                info.deletedAt()
            );
        }
    }

    public record PageResponse(
        List<AdminProductSummary> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        public static PageResponse from(Page<ProductAdminInfo> page) {
            List<AdminProductSummary> content = page.getContent().stream()
                .map(AdminProductSummary::from)
                .toList();
            return new PageResponse(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
            );
        }
    }
}
