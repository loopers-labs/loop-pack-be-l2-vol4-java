package com.loopers.interfaces.api.order;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import com.loopers.interfaces.api.auth.LoginUser;
import com.loopers.support.utils.DateTimeUtil;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
public class OrderV1Controller implements OrderV1ApiSpec {

    private final OrderFacade orderFacade;
    private final DateTimeUtil dateTimeUtil;

    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderV1Dto.OrderResponse> createOrder(
        @Valid @RequestBody OrderV1Dto.CreateRequest request,
        @LoginUser AuthenticatedUser loginUser
    ) {
        OrderInfo orderInfo = orderFacade.createOrder(loginUser.userId(), request.toCommandItems(), dateTimeUtil.now());

        return ApiResponse.success(OrderV1Dto.OrderResponse.from(orderInfo));
    }

    @Override
    @GetMapping
    public ApiResponse<OrderV1Dto.PageResponse> readMyOrders(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startAt,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endAt,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @LoginUser AuthenticatedUser loginUser
    ) {
        Page<OrderInfo> ordersInfo = orderFacade.readMyOrders(loginUser.userId(), startAt, endAt, page, size);

        return ApiResponse.success(OrderV1Dto.PageResponse.from(ordersInfo));
    }

    @Override
    @GetMapping("/{orderId}")
    public ApiResponse<OrderV1Dto.OrderResponse> readMyOrder(
        @PathVariable Long orderId,
        @LoginUser AuthenticatedUser loginUser
    ) {
        OrderInfo orderInfo = orderFacade.readMyOrder(loginUser.userId(), orderId);

        return ApiResponse.success(OrderV1Dto.OrderResponse.from(orderInfo));
    }
}
