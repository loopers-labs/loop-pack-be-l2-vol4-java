package com.loopers.interfaces.api.order;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.domain.Page;

import com.loopers.application.order.OrderAdminInfo;
import com.loopers.application.order.OrderAdminSummaryInfo;
import com.loopers.application.order.OrderItemInfo;

public class OrderAdminV1Dto {

    public record SummaryResponse(
        Long orderId,
        Long userId,
        String status,
        ZonedDateTime orderedAt,
        Integer originalAmount,
        Integer discountAmount,
        Integer finalAmount
    ) {

        public static SummaryResponse from(OrderAdminSummaryInfo orderAdminSummaryInfo) {
            return new SummaryResponse(
                orderAdminSummaryInfo.orderId(),
                orderAdminSummaryInfo.userId(),
                orderAdminSummaryInfo.status().name(),
                orderAdminSummaryInfo.orderedAt(),
                orderAdminSummaryInfo.originalAmount(),
                orderAdminSummaryInfo.discountAmount(),
                orderAdminSummaryInfo.finalAmount()
            );
        }
    }

    public record PageResponse(
        List<SummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {

        public static PageResponse from(Page<OrderAdminSummaryInfo> ordersAdminSummaryInfo) {
            List<SummaryResponse> content = ordersAdminSummaryInfo.getContent()
                .stream()
                .map(SummaryResponse::from)
                .toList();

            return new PageResponse(
                content,
                ordersAdminSummaryInfo.getNumber(),
                ordersAdminSummaryInfo.getSize(),
                ordersAdminSummaryInfo.getTotalElements(),
                ordersAdminSummaryInfo.getTotalPages()
            );
        }
    }

    public record ItemResponse(
        Long productId,
        String productName,
        String brandName,
        Integer unitPrice,
        Integer quantity
    ) {

        public static ItemResponse from(OrderItemInfo orderItemInfo) {
            return new ItemResponse(
                orderItemInfo.productId(),
                orderItemInfo.productName(),
                orderItemInfo.brandName(),
                orderItemInfo.unitPrice(),
                orderItemInfo.quantity()
            );
        }
    }

    public record DetailResponse(
        Long orderId,
        Long userId,
        String status,
        ZonedDateTime orderedAt,
        Integer originalAmount,
        Integer discountAmount,
        Integer finalAmount,
        Long userCouponId,
        List<ItemResponse> items
    ) {

        public static DetailResponse from(OrderAdminInfo orderAdminInfo) {
            return new DetailResponse(
                orderAdminInfo.orderId(),
                orderAdminInfo.userId(),
                orderAdminInfo.status().name(),
                orderAdminInfo.orderedAt(),
                orderAdminInfo.originalAmount(),
                orderAdminInfo.discountAmount(),
                orderAdminInfo.finalAmount(),
                orderAdminInfo.userCouponId(),
                orderAdminInfo.items()
                    .stream()
                    .map(ItemResponse::from)
                    .toList()
            );
        }
    }
}
