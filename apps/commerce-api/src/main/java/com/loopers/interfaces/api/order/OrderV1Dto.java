package com.loopers.interfaces.api.order;

import java.util.List;

public class OrderV1Dto {

    public record CreateRequest(List<OrderItemDto> items) {
        public record OrderItemDto(Long productId, int quantity) {}
    }

    public record CreateResponse(Long orderId) {}
}
