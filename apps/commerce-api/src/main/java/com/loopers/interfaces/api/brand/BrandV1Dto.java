package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandInfo;

public class BrandV1Dto {

    public record CustomerResponse(
        Long id,
        String name,
        String description
    ) {
        public static CustomerResponse from(BrandInfo info) {
            return new CustomerResponse(
                info.id(),
                info.name(),
                info.description()
            );
        }
    }
}
