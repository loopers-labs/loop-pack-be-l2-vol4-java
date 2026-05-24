package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.CreateBrandCommand;
import com.loopers.application.brand.UpdateBrandCommand;
import io.swagger.v3.oas.annotations.media.Schema;

public class BrandAdminV1Dto {

    @Schema(name = "CreateBrandRequest", description = "어드민 브랜드 생성 요청")
    public record CreateBrandRequest(
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
