package com.loopers.interfaces.api.order.admin;

import com.loopers.application.order.AdminOrderInfo;
import com.loopers.domain.order.OrderStatus;
import org.springframework.data.domain.Page;

import java.time.ZonedDateTime;
import java.util.List;

public class AdminOrderV1Dto {

    public record AdminOrderSummary(
        Long id,
        Long userId,
        String buyerLoginId,
        Long totalAmount,
        OrderStatus status,
        ZonedDateTime createdAt
    ) {
        public static AdminOrderSummary from(AdminOrderInfo info) {
            return new AdminOrderSummary(
                info.id(),
                info.userId(),
                info.buyerLoginId(),
                info.totalAmount(),
                info.status(),
                info.createdAt()
            );
        }
    }

    public record AdminOrderDetail(
        Long id,
        Long userId,
        String buyerLoginId,
        Long totalAmount,
        OrderStatus status,
        List<Item> items,
        ZonedDateTime createdAt
    ) {
        public record Item(
            Long productId,
            Integer quantity,
            String productName,
            Long productPrice,
            String brandName
        ) {
            public static Item from(com.loopers.application.order.OrderInfo.Item info) {
                return new Item(
                    info.productId(),
                    info.quantity(),
                    info.productName(),
                    info.productPrice(),
                    info.brandName()
                );
            }
        }

        public static AdminOrderDetail from(AdminOrderInfo info) {
            return new AdminOrderDetail(
                info.id(),
                info.userId(),
                info.buyerLoginId(),
                info.totalAmount(),
                info.status(),
                info.items().stream().map(Item::from).toList(),
                info.createdAt()
            );
        }
    }

    public record PageResponse(
        List<AdminOrderSummary> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        public static PageResponse from(Page<AdminOrderInfo> page) {
            List<AdminOrderSummary> content = page.getContent().stream()
                .map(AdminOrderSummary::from)
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
