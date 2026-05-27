package com.loopers.brand.interfaces.api;

import com.loopers.brand.application.BrandResult;

public class BrandV1Response {

    public record Detail(
        Long id,
        String name,
        String description
    ) {
        public static Detail from(BrandResult.Detail result) {
            return new Detail(result.id(), result.name(), result.description());
        }
    }
}
