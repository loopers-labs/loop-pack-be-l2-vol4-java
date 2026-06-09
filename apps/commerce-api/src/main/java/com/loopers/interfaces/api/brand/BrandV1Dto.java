package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandInfo;
import jakarta.validation.constraints.NotBlank;

import java.time.ZonedDateTime;
import java.util.UUID;

public class BrandV1Dto {

    public record CreateRequest(
        @NotBlank String name,
        @NotBlank String description
    ) {}

    public record UpdateRequest(
        @NotBlank String name,
        @NotBlank String description
    ) {}

    public record BrandResponse(
        UUID id,
        String name,
        String description,
        ZonedDateTime createdAt
    ) {
        public static BrandResponse from(BrandInfo info) {
            return new BrandResponse(
                info.id(),
                info.name(),
                info.description(),
                info.createdAt()
            );
        }
    }
}
