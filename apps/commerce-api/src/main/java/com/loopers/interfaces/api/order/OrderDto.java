package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderInfo;
import com.loopers.domain.order.OrderProductCommand;
import com.loopers.domain.order.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public final class OrderDto {

    private OrderDto() {}

    public static final class Create {

        private Create() {}

        public static final class V1 {

            private V1() {}

            public record Request(
                @NotEmpty
                java.util.List<@Valid ProductRequest> items
            ) {
                public java.util.List<OrderProductCommand> toCommands() {
                    return items.stream()
                        .map(product -> new OrderProductCommand(product.productId(), product.quantity()))
                        .toList();
                }
            }

            public record ProductRequest(
                @NotNull
                Long productId,
                @NotNull
                @Positive
                Integer quantity
            ) {}

            public record Response(
                Long id,
                String userLoginId,
                OrderStatus status,
                Long totalAmount,
                java.util.List<LineResponse> orderLines,
                java.util.List<FailureResponse> failures
            ) {
                public static Response from(OrderInfo info) {
                    return new Response(
                        info.id(),
                        info.userLoginId(),
                        info.status(),
                        info.totalAmount(),
                        info.orderLines().stream()
                            .map(LineResponse::from)
                            .toList(),
                        info.failures().stream()
                            .map(FailureResponse::from)
                            .toList()
                    );
                }
            }

            public record LineResponse(
                Long productId,
                String productName,
                Long price,
                Integer quantity,
                Long amount
            ) {
                public static LineResponse from(OrderInfo.OrderLineInfo info) {
                    return new LineResponse(
                        info.productId(),
                        info.productName(),
                        info.price(),
                        info.quantity(),
                        info.amount()
                    );
                }
            }

            public record FailureResponse(
                Long productId,
                Integer quantity,
                String reason
            ) {
                public static FailureResponse from(OrderInfo.OrderFailureInfo info) {
                    return new FailureResponse(
                        info.productId(),
                        info.quantity(),
                        info.reason()
                    );
                }
            }
        }
    }

    public static final class Get {

        private Get() {}

        public static final class V1 {

            private V1() {}

            public record Response(
                Long id,
                String userLoginId,
                OrderStatus status,
                Long totalAmount,
                java.util.List<LineResponse> orderLines,
                java.util.List<FailureResponse> failures
            ) {
                public static Response from(OrderInfo info) {
                    return new Response(
                        info.id(),
                        info.userLoginId(),
                        info.status(),
                        info.totalAmount(),
                        info.orderLines().stream()
                            .map(LineResponse::from)
                            .toList(),
                        info.failures().stream()
                            .map(FailureResponse::from)
                            .toList()
                    );
                }
            }

            public record LineResponse(
                Long productId,
                String productName,
                Long price,
                Integer quantity,
                Long amount
            ) {
                public static LineResponse from(OrderInfo.OrderLineInfo info) {
                    return new LineResponse(
                        info.productId(),
                        info.productName(),
                        info.price(),
                        info.quantity(),
                        info.amount()
                    );
                }
            }

            public record FailureResponse(
                Long productId,
                Integer quantity,
                String reason
            ) {
                public static FailureResponse from(OrderInfo.OrderFailureInfo info) {
                    return new FailureResponse(
                        info.productId(),
                        info.quantity(),
                        info.reason()
                    );
                }
            }
        }
    }

    public static final class List {

        private List() {}

        public static final class V1 {

            private V1() {}

            public record Response(
                Long id,
                String userLoginId,
                OrderStatus status,
                Long totalAmount,
                java.util.List<LineResponse> orderLines,
                java.util.List<FailureResponse> failures
            ) {
                public static Response from(OrderInfo info) {
                    return new Response(
                        info.id(),
                        info.userLoginId(),
                        info.status(),
                        info.totalAmount(),
                        info.orderLines().stream()
                            .map(LineResponse::from)
                            .toList(),
                        info.failures().stream()
                            .map(FailureResponse::from)
                            .toList()
                    );
                }
            }

            public record LineResponse(
                Long productId,
                String productName,
                Long price,
                Integer quantity,
                Long amount
            ) {
                public static LineResponse from(OrderInfo.OrderLineInfo info) {
                    return new LineResponse(
                        info.productId(),
                        info.productName(),
                        info.price(),
                        info.quantity(),
                        info.amount()
                    );
                }
            }

            public record FailureResponse(
                Long productId,
                Integer quantity,
                String reason
            ) {
                public static FailureResponse from(OrderInfo.OrderFailureInfo info) {
                    return new FailureResponse(
                        info.productId(),
                        info.quantity(),
                        info.reason()
                    );
                }
            }
        }
    }
}
