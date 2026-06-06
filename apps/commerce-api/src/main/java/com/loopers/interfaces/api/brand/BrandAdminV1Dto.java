package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandAdminInfo;
import org.springframework.data.domain.Page;

import java.time.ZonedDateTime;
import java.util.List;

public class BrandAdminV1Dto {

    public record CreateRequest(
        String name,
        String description
    ) {}

    public record UpdateRequest(
        String name,
        String description
    ) {}

    public record Response(
        Long id,
        String name,
        String description,
        ZonedDateTime deletedAt
    ) {
        public static Response from(BrandAdminInfo info) {
            return new Response(
                info.id(),
                info.name(),
                info.description(),
                info.deletedAt()
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
            List<Response> content = page.getContent().stream()
                .map(Response::from)
                .toList();
            return new PageResponse(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
            );
        }
    }
}
