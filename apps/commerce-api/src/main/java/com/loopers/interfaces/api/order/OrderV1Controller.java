package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.domain.order.OrderItemInput;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
public class OrderV1Controller implements OrderV1ApiSpec {

    private final OrderFacade orderFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public ApiResponse<OrderV1Dto.OrderResponse> createOrder(
            @Valid @RequestBody OrderV1Dto.OrderRequest request,
            @RequestAttribute("authenticatedUserId") Long userId
    ) {
        List<OrderItemInput> items = request.items().stream()
                .map(req -> new OrderItemInput(req.stockId(), req.quantity()))
                .toList();
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(orderFacade.createOrder(userId, items)));
    }

    @GetMapping
    @Override
    public ApiResponse<List<OrderV1Dto.OrderResponse>> getOrders(
            @RequestAttribute("authenticatedUserId") Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startAt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endAt
    ) {
        return ApiResponse.success(orderFacade.getOrders(userId, startAt, endAt).stream()
                .map(OrderV1Dto.OrderResponse::from)
                .toList());
    }

    @GetMapping("/{orderId}")
    @Override
    public ApiResponse<OrderV1Dto.OrderResponse> getOrder(
            @PathVariable Long orderId,
            @RequestAttribute("authenticatedUserId") Long userId
    ) {
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(orderFacade.getOrder(orderId)));
    }

    @PostMapping("/{orderId}/cancel")
    @ResponseStatus(HttpStatus.OK)
    @Override
    public ApiResponse<OrderV1Dto.OrderResponse> cancelOrder(
            @PathVariable Long orderId,
            @RequestAttribute("authenticatedUserId") Long userId
    ) {
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(orderFacade.cancelOrder(orderId)));
    }

}
