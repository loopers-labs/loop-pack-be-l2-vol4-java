package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderApplicationService;
import com.loopers.application.order.OrderInfo;
import com.loopers.domain.order.OrderItemCommand;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.AuthUser;
import com.loopers.interfaces.api.user.AuthUserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
public class OrderV1Controller {

    private final OrderApplicationService orderApplicationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderV1Dto.OrderResponse> createOrder(
        @AuthUser AuthUserContext authUser,
        @Valid @RequestBody OrderV1Dto.CreateOrderRequest request
    ) {
        List<OrderItemCommand> commands = request.items().stream()
            .map(item -> new OrderItemCommand(item.productId(), item.quantity()))
            .toList();
        OrderInfo info = orderApplicationService.createOrder(authUser.userId(), commands, request.couponId());
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(info));
    }

    @GetMapping
    public ApiResponse<List<OrderV1Dto.OrderResponse>> getOrders(
        @AuthUser AuthUserContext authUser,
        @RequestParam(required = false) ZonedDateTime startAt,
        @RequestParam(required = false) ZonedDateTime endAt
    ) {
        ZonedDateTime resolvedStartAt = startAt != null ? startAt : ZonedDateTime.now().minusDays(30);
        ZonedDateTime resolvedEndAt = endAt != null ? endAt : ZonedDateTime.now();
        List<OrderInfo> infos = orderApplicationService.getOrders(authUser.userId(), resolvedStartAt, resolvedEndAt);
        List<OrderV1Dto.OrderResponse> responses = infos.stream()
            .map(OrderV1Dto.OrderResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderV1Dto.OrderResponse> getOrder(
        @AuthUser AuthUserContext authUser,
        @PathVariable Long orderId
    ) {
        OrderInfo info = orderApplicationService.getOrder(authUser.userId(), orderId);
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(info));
    }
}
