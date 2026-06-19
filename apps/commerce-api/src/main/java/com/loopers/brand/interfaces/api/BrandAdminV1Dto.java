package com.loopers.brand.interfaces.api;

import com.loopers.brand.application.BrandInfo;
import com.loopers.brand.application.CreateBrandCommand;
import com.loopers.brand.application.UpdateBrandCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.time.ZonedDateTime;

public class BrandAdminV1Dto {

    @Schema(name = "AdminBrandResponse", description = "어드민 브랜드 정보 응답")
    public record BrandResponse(
        Long id,
        String name,
        String description,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt,
        ZonedDateTime deletedAt
    ) {
        public static BrandResponse from(BrandInfo info) {
            return new BrandResponse(
                info.id(),
                info.name(),
                info.description(),
                info.createdAt(),
                info.updatedAt(),
                info.deletedAt()
            );
        }
    }

    @Schema(name = "CreateBrandRequest", description = "어드민 브랜드 생성 요청")
    public record CreateBrandRequest(
        @NotBlank(message = "브랜드명은 비어있을 수 없습니다.")
        @Schema(description = "브랜드명", example = "애플", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(description = "브랜드 설명", example = "기술과 디자인으로 일상을 새롭게 만드는 브랜드")
        String description
    ) {
        public CreateBrandCommand toCommand() {
            return new CreateBrandCommand(name, description);
        }
    }

    @Schema(name = "UpdateBrandRequest", description = "어드민 브랜드 수정 요청")
    public record UpdateBrandRequest(
        @NotBlank(message = "브랜드명은 비어있을 수 없습니다.")
        @Schema(description = "브랜드명", example = "애플 스토어", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(description = "브랜드 설명", example = "사용자 경험과 서비스를 함께 제공하는 브랜드")
        String description
    ) {
        public UpdateBrandCommand toCommand(Long brandId) {
            return new UpdateBrandCommand(brandId, name, description);
        }
    }
}
