package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandAdminInfo;
import org.springframework.data.domain.Page;

import java.time.ZonedDateTime;
import java.util.List;

public class BrandAdminV1Dto {

    public record Response(
        Long id,
        String name,
        String description,
        long productCount,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
    ) {
        public static Response from(BrandAdminInfo info) {
            return new Response(
                info.id(),
                info.name(),
                info.description(),
                info.productCount(),
                info.createdAt(),
                info.updatedAt()
            );
        }
    }

    public record PageResponse(
        List<Response> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        public static PageResponse from(Page<BrandAdminInfo> page) {
            return new PageResponse(
                page.getContent().stream().map(Response::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
            );
        }
    }
}
