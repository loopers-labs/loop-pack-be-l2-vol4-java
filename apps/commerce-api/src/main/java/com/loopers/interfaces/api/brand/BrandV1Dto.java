package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandInfo;
import io.swagger.v3.oas.annotations.media.Schema;

public class BrandV1Dto {

    @Schema(name = "BrandResponse", description = "브랜드 정보 응답")
    public record BrandResponse(
        @Schema(description = "브랜드 PK", example = "1")
        Long id,
        @Schema(description = "브랜드명", example = "애플")
        String name,
        @Schema(description = "브랜드 설명", example = "기술과 디자인으로 일상을 새롭게 만드는 브랜드")
        String description
    ) {
        public static BrandResponse from(BrandInfo info) {
            return new BrandResponse(
                info.id(),
                info.name(),
                info.description()
            );
        }
    }
}
