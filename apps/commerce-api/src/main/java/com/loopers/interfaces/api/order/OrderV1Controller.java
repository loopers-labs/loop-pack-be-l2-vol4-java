package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.domain.order.Order;
import com.loopers.application.order.OrderService;
import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.LoginUser;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
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

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
public class OrderV1Controller {

    private final OrderFacade orderFacade;
    private final OrderService orderService;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<OrderV1Dto.OrderCreateResponse> createOrder(
            @LoginUser User user,
            @RequestBody OrderV1Dto.OrderCreateRequest request
    ) {
        OrderInfo.Create info = orderFacade.createOrder(request.toCommand(user.getId()));
        return ApiResponse.success(OrderV1Dto.OrderCreateResponse.from(info));
    }

    @GetMapping
    public ApiResponse<List<OrderV1Dto.OrderSummary>> getOrders(
            @LoginUser User user,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startAt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endAt
    ) {
        if (endAt.isBefore(startAt)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "종료일은 시작일보다 이전일 수 없습니다.");
        }

        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime start = startAt.atStartOfDay(zone);
        ZonedDateTime end = endAt.atTime(23, 59, 59).atZone(zone);

        List<Order> orders = orderService.getOrders(user.getId(), start, end);
        return ApiResponse.success(orders.stream().map(OrderV1Dto.OrderSummary::from).toList());
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderV1Dto.OrderResponse> getOrder(
            @LoginUser User user,
            @PathVariable Long orderId
    ) {
        OrderInfo.Detail detail = orderFacade.getOrder(orderId, user.getId());
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(detail));
    }
}
