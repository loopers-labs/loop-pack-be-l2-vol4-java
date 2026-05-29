package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderApplicationService;
import com.loopers.domain.user.UserModel;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.interceptor.AuthInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
public class OrderV1Controller {

    private final OrderApplicationService orderApplicationService;

    @PostMapping
    public ApiResponse<OrderV1Dto.OrderResponse> createOrder(
        @RequestBody @Valid OrderV1Dto.CreateOrderRequest request,
        HttpServletRequest httpRequest
    ) {
        UserModel user = (UserModel) httpRequest.getAttribute(AuthInterceptor.AUTHENTICATED_USER);
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(
            orderApplicationService.createOrder(user.getId(), request.toCommand())
        ));
    }

    @GetMapping
    public ApiResponse<List<OrderV1Dto.OrderResponse>> getOrders(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startAt,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endAt,
        HttpServletRequest httpRequest
    ) {
        UserModel user = (UserModel) httpRequest.getAttribute(AuthInterceptor.AUTHENTICATED_USER);
        return ApiResponse.success(
            orderApplicationService.getOrders(user.getId(), startAt, endAt).stream()
                .map(OrderV1Dto.OrderResponse::from)
                .toList()
        );
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderV1Dto.OrderResponse> getOrder(
        @PathVariable @Min(1) Long orderId,
        HttpServletRequest httpRequest
    ) {
        UserModel user = (UserModel) httpRequest.getAttribute(AuthInterceptor.AUTHENTICATED_USER);
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(
            orderApplicationService.getOrder(user.getId(), orderId)
        ));
    }
}
