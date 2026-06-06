package com.loopers.interfaces.api.order;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.loopers.application.order.OrderCriteria;
import com.loopers.application.order.OrderInfo;
import com.loopers.domain.common.PageResult;
import com.loopers.domain.order.OrderStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class OrderV1Dto {

    private OrderV1Dto() {
    }

    public record PlaceRequest(Long couponId, List<LineRequest> lines) {

        public List<OrderCriteria.Line> toCriteriaLines() {
            return lines == null ? List.of()
                    : lines.stream().map(l -> new OrderCriteria.Line(l.productId(), l.quantity())).toList();
        }
    }

    public record LineRequest(Long productId, Integer quantity) {
    }

    public record ItemResponse(
            Long productId,
            String productName,
            long unitPrice,
            int quantity,
            long subtotal
    ) {

        public static ItemResponse from(OrderInfo.Item info) {
            return new ItemResponse(
                    info.productId(),
                    info.productName(),
                    info.unitPrice(),
                    info.quantity(),
                    info.subtotal()
            );
        }
    }

    public record CreatedResponse(
            Long id,
            Long userId,
            long originalAmount,
            long discountAmount,
            long totalAmount,
            OrderStatus status,
            List<ItemResponse> items,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
            LocalDateTime orderedAt
    ) {

        public static CreatedResponse from(OrderInfo.Created info) {
            return new CreatedResponse(
                    info.id(),
                    info.userId(),
                    info.originalAmount(),
                    info.discountAmount(),
                    info.totalAmount(),
                    info.status(),
                    info.items().stream().map(ItemResponse::from).toList(),
                    info.orderedAt()
            );
        }
    }

    public record DetailResponse(
            Long id,
            Long userId,
            long originalAmount,
            long discountAmount,
            long totalAmount,
            OrderStatus status,
            List<ItemResponse> items,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
            LocalDateTime orderedAt
    ) {

        public static DetailResponse from(OrderInfo.Detail info) {
            return new DetailResponse(
                    info.id(),
                    info.userId(),
                    info.originalAmount(),
                    info.discountAmount(),
                    info.totalAmount(),
                    info.status(),
                    info.items().stream().map(ItemResponse::from).toList(),
                    info.orderedAt()
            );
        }
    }

    public record ListItemResponse(
            Long id,
            Long userId,
            long totalAmount,
            OrderStatus status,
            int itemCount,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
            LocalDateTime orderedAt
    ) {

        public static ListItemResponse from(OrderInfo.ListItem info) {
            return new ListItemResponse(
                    info.id(),
                    info.userId(),
                    info.totalAmount(),
                    info.status(),
                    info.itemCount(),
                    info.orderedAt()
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

        public static PageResponse from(PageResult<OrderInfo.ListItem> result) {
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
