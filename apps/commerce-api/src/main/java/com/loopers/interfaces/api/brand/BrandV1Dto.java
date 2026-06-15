package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandInfo;
import com.loopers.domain.common.PageResult;

import java.util.List;

public final class BrandV1Dto {

    private BrandV1Dto() {
    }

    public record RegisterRequest(String name, String description) {
    }

    public record ModifyRequest(String name, String description) {
    }

    public record BrandResponse(Long id, String name, String description) {

        public static BrandResponse from(BrandInfo info) {
            return new BrandResponse(info.id(), info.name(), info.description());
        }
    }

    public record PageResponse(
            List<BrandResponse> content,
            int page,
            int size,
            boolean hasNext,
            long totalElements
    ) {

        public static PageResponse from(PageResult<BrandInfo> result) {
            return new PageResponse(
                    result.content().stream().map(BrandResponse::from).toList(),
                    result.page(),
                    result.size(),
                    result.hasNext(),
                    result.totalElements()
            );
        }
    }
}
