package com.loopers.order.interfaces;

import com.loopers.order.application.OrderFacade;
import com.loopers.order.application.OrderInfo;
import com.loopers.order.application.OrderItemCommand;
import com.loopers.support.auth.CurrentUser;
import com.loopers.support.auth.LoginUser;
import com.loopers.support.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
public class OrderV1Controller {

    private final OrderFacade orderFacade;

    @PostMapping
    public ApiResponse<OrderV1Dto.OrderResponse> createOrder(
        @CurrentUser LoginUser loginUser,
        @RequestBody OrderV1Dto.CreateRequest request
    ) {
        List<OrderItemCommand> commands = request.items().stream()
            .map(item -> new OrderItemCommand(item.productId(), item.quantity()))
            .toList();
        OrderInfo info = orderFacade.createOrder(loginUser.id(), commands, request.couponId());
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(info));
    }

    @GetMapping
    public ApiResponse<List<OrderV1Dto.OrderResponse>> getOrders(
        @CurrentUser LoginUser loginUser,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startAt,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endAt
    ) {
        List<OrderInfo> infos = orderFacade.getOrders(loginUser.id(), startAt, endAt);
        List<OrderV1Dto.OrderResponse> responses = infos.stream()
            .map(OrderV1Dto.OrderResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }

    @PostMapping("/{orderId}/pay/start")
    public ApiResponse<OrderV1Dto.OrderResponse> startPayment(
        @CurrentUser LoginUser loginUser,
        @PathVariable Long orderId
    ) {
        OrderInfo info = orderFacade.startPayment(loginUser.id(), orderId);
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(info));
    }

    @PostMapping("/{orderId}/pay/confirm")
    public ApiResponse<OrderV1Dto.OrderResponse> confirmPayment(
        @CurrentUser LoginUser loginUser,
        @PathVariable Long orderId
    ) {
        OrderInfo info = orderFacade.confirmPayment(loginUser.id(), orderId);
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(info));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderV1Dto.OrderResponse> getOrder(
        @CurrentUser LoginUser loginUser,
        @PathVariable Long orderId
    ) {
        OrderInfo info = orderFacade.getOrder(loginUser.id(), orderId);
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(info));
    }
}
