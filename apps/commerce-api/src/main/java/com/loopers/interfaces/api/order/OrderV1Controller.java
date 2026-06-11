package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.AuthHeaders;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
            @RequestHeader(AuthHeaders.LOGIN_ID) String loginId,
            @RequestHeader(AuthHeaders.LOGIN_PW) String loginPw,
            @Valid @RequestBody OrderV1Dto.CreateRequest request
    ) {
        OrderInfo info = orderFacade.createOrder(loginId, loginPw, request.items().stream()
                .map(item -> new OrderFacade.OrderItemDto(item.productId(), item.quantity()))
                .toList(), request.couponId());
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(info));
    }

    @GetMapping
    public ApiResponse<List<OrderV1Dto.OrderResponse>> getOrders(
            @RequestHeader(AuthHeaders.LOGIN_ID) String loginId,
            @RequestHeader(AuthHeaders.LOGIN_PW) String loginPw,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startAt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endAt
    ) {
        List<OrderInfo> infos = orderFacade.getOrders(loginId, loginPw, startAt, endAt);
        return ApiResponse.success(infos.stream().map(OrderV1Dto.OrderResponse::from).toList());
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderV1Dto.OrderResponse> getOrder(
            @RequestHeader(AuthHeaders.LOGIN_ID) String loginId,
            @RequestHeader(AuthHeaders.LOGIN_PW) String loginPw,
            @PathVariable Long orderId
    ) {
        OrderInfo info = orderFacade.getOrder(loginId, loginPw, orderId);
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(info));
    }
}
