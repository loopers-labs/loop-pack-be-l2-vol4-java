package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.AuthenticatedUser;
import com.loopers.interfaces.auth.LoginUser;
import jakarta.validation.Valid;
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
    public ApiResponse<OrderDto.Create.V1.Response> createOrder(
        @LoginUser AuthenticatedUser user,
        @Valid @RequestBody OrderDto.Create.V1.Request request
    ) {
        OrderInfo info = orderFacade.createOrder(user.loginId(), request.toCommands(), request.couponId());
        OrderDto.Create.V1.Response response = OrderDto.Create.V1.Response.from(info);
        return ApiResponse.success(response);
    }

    @GetMapping
    public ApiResponse<List<OrderDto.List.V1.Response>> getOrders(
        @LoginUser AuthenticatedUser user,
        @RequestParam(value = "startAt", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startAt,
        @RequestParam(value = "endAt", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endAt,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size
    ) {
        List<OrderDto.List.V1.Response> responses = orderFacade.getOrders(user.loginId(), startAt, endAt, page, size).stream()
            .map(OrderDto.List.V1.Response::from)
            .toList();
        return ApiResponse.success(responses);
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderDto.Get.V1.Response> getOrder(
        @LoginUser AuthenticatedUser user,
        @PathVariable(value = "orderId") Long orderId
    ) {
        OrderInfo info = orderFacade.getOrder(user.loginId(), orderId);
        OrderDto.Get.V1.Response response = OrderDto.Get.V1.Response.from(info);
        return ApiResponse.success(response);
    }
}
