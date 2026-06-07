package com.loopers.interfaces.api.product;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.domain.Page;

import com.loopers.application.product.ProductAdminInfo;
import com.loopers.application.product.ProductCreateInfo;
import com.loopers.application.product.ProductUpdateInfo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public class ProductAdminV1Dto {

    public record CreateRequest(
        @NotNull(message = "브랜드 식별자는 null일 수 없습니다.")
        Long brandId,

        @NotBlank(message = "상품 이름은 null이거나 빈 값일 수 없습니다.")
        String name,

        String description,

        @NotNull(message = "상품 가격은 null일 수 없습니다.")
        @PositiveOrZero(message = "상품 가격은 0 이상이어야 합니다.")
        Integer price,

        @NotNull(message = "상품 재고는 null일 수 없습니다.")
        @PositiveOrZero(message = "상품 재고는 0 이상이어야 합니다.")
        Integer stock
    ) {
    }

    public record CreateResponse(Long productId) {

        public static CreateResponse from(ProductCreateInfo productCreateInfo) {
            return new CreateResponse(productCreateInfo.productId());
        }
    }

    public record UpdateRequest(
        @NotBlank(message = "상품 이름은 null이거나 빈 값일 수 없습니다.")
        String name,

        String description,

        @NotNull(message = "상품 가격은 null일 수 없습니다.")
        @PositiveOrZero(message = "상품 가격은 0 이상이어야 합니다.")
        Integer price,

        @NotNull(message = "상품 재고는 null일 수 없습니다.")
        @PositiveOrZero(message = "상품 재고는 0 이상이어야 합니다.")
        Integer stock
    ) {
    }

    public record UpdateResponse(Long productId) {

        public static UpdateResponse from(ProductUpdateInfo productUpdateInfo) {
            return new UpdateResponse(productUpdateInfo.productId());
        }
    }

    public record BrandResponse(Long brandId, String name) {
    }

    public record DetailResponse(
        Long productId,
        String name,
        String description,
        BrandResponse brand,
        Integer price,
        Integer stock,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
    ) {

        public static DetailResponse from(ProductAdminInfo productAdminInfo) {
            return new DetailResponse(
                productAdminInfo.productId(),
                productAdminInfo.name(),
                productAdminInfo.description(),
                new BrandResponse(productAdminInfo.brandId(), productAdminInfo.brandName()),
                productAdminInfo.price(),
                productAdminInfo.stock(),
                productAdminInfo.createdAt(),
                productAdminInfo.updatedAt()
            );
        }
    }

    public record PageResponse(
        List<DetailResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {

        public static PageResponse from(Page<ProductAdminInfo> productsAdminInfo) {
            List<DetailResponse> content = productsAdminInfo.getContent()
                .stream()
                .map(DetailResponse::from)
                .toList();

            return new PageResponse(
                content,
                productsAdminInfo.getNumber(),
                productsAdminInfo.getSize(),
                productsAdminInfo.getTotalElements(),
                productsAdminInfo.getTotalPages()
            );
        }
    }
}
