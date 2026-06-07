package com.loopers.interfaces.api.brand;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.domain.Page;

import com.loopers.application.brand.BrandCreateInfo;
import com.loopers.application.brand.BrandInfo;
import com.loopers.application.brand.BrandUpdateInfo;

import jakarta.validation.constraints.NotBlank;

public class BrandAdminV1Dto {

    public record CreateRequest(
        @NotBlank(message = "브랜드 이름은 null이거나 빈 값일 수 없습니다.")
        String name,

        String description
    ) {
    }

    public record CreateResponse(Long brandId) {

        public static CreateResponse from(BrandCreateInfo brandCreateInfo) {
            return new CreateResponse(brandCreateInfo.brandId());
        }
    }

    public record UpdateRequest(
        @NotBlank(message = "브랜드 이름은 null이거나 빈 값일 수 없습니다.")
        String name,

        String description
    ) {
    }

    public record UpdateResponse(Long brandId) {

        public static UpdateResponse from(BrandUpdateInfo brandUpdateInfo) {
            return new UpdateResponse(brandUpdateInfo.brandId());
        }
    }

    public record DetailResponse(
        Long brandId,
        String name,
        String description,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
    ) {

        public static DetailResponse from(BrandInfo brandInfo) {
            return new DetailResponse(
                brandInfo.brandId(),
                brandInfo.name(),
                brandInfo.description(),
                brandInfo.createdAt(),
                brandInfo.updatedAt()
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

        public static PageResponse from(Page<BrandInfo> brandsInfo) {
            List<DetailResponse> content = brandsInfo.getContent()
                .stream()
                .map(DetailResponse::from)
                .toList();

            return new PageResponse(
                content,
                brandsInfo.getNumber(),
                brandsInfo.getSize(),
                brandsInfo.getTotalElements(),
                brandsInfo.getTotalPages()
            );
        }
    }
}
