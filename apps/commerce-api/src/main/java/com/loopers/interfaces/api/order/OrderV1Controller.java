package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResult;
import com.loopers.interfaces.auth.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
public class OrderV1Controller implements OrderV1ApiSpec {

    private final OrderFacade orderFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderV1Dto.CreateOrderResponse> createOrder(
            @Valid @RequestBody OrderV1Dto.CreateOrderRequest request,
            @LoginUser Long userId
    ) {
        return ApiResponse.success(
                OrderV1Dto.CreateOrderResponse.from(
                        orderFacade.createOrder(userId, request.toCommands())
                )
        );
    }

    @GetMapping
    public ApiResponse<PageResult<OrderV1Dto.OrderResponse>> getOrders(
            @LoginUser Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime startAt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime endAt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(
                PageResult.from(
                        orderFacade.getOrders(userId, startAt, endAt, PageRequest.of(page, size))
                                .map(OrderV1Dto.OrderResponse::from)
                )
        );
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderV1Dto.OrderResponse> getOrder(
            @PathVariable Long orderId,
            @LoginUser Long userId
    ) {
        return ApiResponse.success(
                OrderV1Dto.OrderResponse.from(orderFacade.getOrder(userId, orderId))
        );
    }
}
