package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;
import com.loopers.domain.common.PageResult;

import java.util.List;

public final class ProductV1Dto {

    private ProductV1Dto() {
    }

    public record RegisterRequest(Long brandId, String name, long price, int stock) {
    }

    public record ModifyRequest(String name, long price, int stock) {
    }

    public record CreatedResponse(
            Long id,
            Long brandId,
            String name,
            long price,
            int stock
    ) {

        public static CreatedResponse from(ProductInfo.Created info) {
            return new CreatedResponse(
                    info.id(),
                    info.brandId(),
                    info.name(),
                    info.price(),
                    info.stock()
            );
        }
    }

    public record DetailResponse(
            Long id,
            Long brandId,
            String brandName,
            String name,
            long price,
            int stock,
            long likeCount,
            boolean soldOut,
            Long rank
    ) {

        /**
         * rank 는 캐시되는 {@code ProductInfo.Detail} 밖에서 매 요청 조회해 붙인다 — 순위는 합성 주기마다
         * 변하는 휘발 값이라 상세 캐시(30s)에 함께 얼리지 않는다. 오늘 보드 미등재면 null.
         */
        public static DetailResponse from(ProductInfo.Detail info, Long rank) {
            return new DetailResponse(
                    info.id(),
                    info.brandId(),
                    info.brandName(),
                    info.name(),
                    info.price(),
                    info.stock(),
                    info.likeCount(),
                    info.soldOut(),
                    rank
            );
        }
    }

    public record ListItemResponse(
            Long id,
            Long brandId,
            String brandName,
            String name,
            long price,
            long likeCount,
            boolean soldOut
    ) {

        public static ListItemResponse from(ProductInfo.ListItem info) {
            return new ListItemResponse(
                    info.id(),
                    info.brandId(),
                    info.brandName(),
                    info.name(),
                    info.price(),
                    info.likeCount(),
                    info.soldOut()
            );
        }
    }

    public record PageResponse(
            List<ListItemResponse> content,
            int page,
            int size,
            boolean hasNext,
            long totalElements
    ) {

        public static PageResponse from(PageResult<ProductInfo.ListItem> result) {
            return new PageResponse(
                    result.content().stream().map(ListItemResponse::from).toList(),
                    result.page(),
                    result.size(),
                    result.hasNext(),
                    result.totalElements()
            );
        }
    }
}
