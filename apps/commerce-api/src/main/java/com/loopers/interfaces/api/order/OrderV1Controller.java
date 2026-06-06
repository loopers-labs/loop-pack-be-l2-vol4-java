package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderApplicationService;
import com.loopers.application.order.OrderCriteria;
import com.loopers.application.order.OrderInfo;
import com.loopers.domain.common.PageResult;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.LoginUser;
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

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
public class OrderV1Controller implements OrderV1ApiSpec {

    private final OrderApplicationService orderApplicationService;

    @PostMapping
    @Override
    public ApiResponse<OrderV1Dto.CreatedResponse> place(
            @LoginUser Long userId,
            @RequestBody OrderV1Dto.PlaceRequest request
    ) {
        OrderInfo.Created info = orderApplicationService.place(
                new OrderCriteria.Place(userId, request.couponId(), request.toCriteriaLines())
        );
        return ApiResponse.success(OrderV1Dto.CreatedResponse.from(info));
    }

    @GetMapping
    @Override
    public ApiResponse<OrderV1Dto.PageResponse> getMyOrders(
            @LoginUser Long userId,
            @RequestParam(value = "startAt", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startAt,
            @RequestParam(value = "endAt", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endAt,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        PageResult<OrderInfo.ListItem> result = orderApplicationService.getMyOrders(
                new OrderCriteria.MySearch(userId, startAt, endAt, page, size)
        );
        return ApiResponse.success(OrderV1Dto.PageResponse.from(result));
    }

    @GetMapping("/{orderId}")
    @Override
    public ApiResponse<OrderV1Dto.DetailResponse> getMyOrder(
            @LoginUser Long userId,
            @PathVariable("orderId") Long orderId
    ) {
        OrderInfo.Detail info = orderApplicationService.getMyOrder(userId, orderId);
        return ApiResponse.success(OrderV1Dto.DetailResponse.from(info));
    }
}
