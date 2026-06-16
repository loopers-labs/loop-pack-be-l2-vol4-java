package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderInfo;
import com.loopers.domain.order.OrderItemInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public class OrderV1Dto {

    public record OrderItemRequest(
            @NotNull(message = "재고 ID는 필수입니다.") Long stockId,
            @Min(value = 1, message = "수량은 1 이상이어야 합니다.") int quantity
    ) {}

    public record OrderRequest(
            @NotNull(message = "주문 번호는 필수입니다.") @Pattern(regexp = "\\d{14}[A-Za-z0-9]{6}", message = "주문 번호는 14자리 날짜(yyyyMMddHHmmss) + 6자리 랜덤 영숫자여야 합니다.") String orderNumber,
            @NotEmpty(message = "주문 항목은 1개 이상이어야 합니다.") @Valid List<OrderItemRequest> items,
            Long userCouponId
    ) {
        public List<OrderItemInput> toInputs() {
            return items.stream()
                    .map(req -> new OrderItemInput(req.stockId(), req.quantity()))
                    .toList();
        }
    }

    public record OrderResponse(
            Long id,
            String orderNumber,
            Long originalAmount,
            Long discountAmount,
            Long totalAmount,
            String status,
            List<OrderItemResponse> items
    ) {
        public record OrderItemResponse(
                Long id,
                Long productId,
                String productName,
                Long productPrice,
                Integer quantity
        ) {
            public static OrderItemResponse from(OrderInfo.OrderItemInfo item) {
                return new OrderItemResponse(
                        item.id(), item.productId(), item.productName(),
                        item.productPrice(), item.quantity()
                );
            }
        }

        public static OrderResponse from(OrderInfo info) {
            return new OrderResponse(
                    info.id(),
                    info.orderNumber(),
                    info.originalAmount(),
                    info.discountAmount(),
                    info.totalAmount(),
                    info.status(),
                    info.items().stream().map(OrderItemResponse::from).toList()
            );
        }

        public static List<OrderResponse> from(List<OrderInfo> infos) {
            return infos.stream().map(OrderResponse::from).toList();
        }
    }
}
