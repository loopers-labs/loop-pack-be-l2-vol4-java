package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.AuthenticatedUser;
import com.loopers.interfaces.auth.LoginUser;
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
public class OrderV1Controller implements OrderV1ApiSpec {

    private final OrderFacade orderFacade;

    @PostMapping
    @Override
    public ApiResponse<OrderV1Dto.OrderResponse> placeOrder(
        @AuthenticatedUser LoginUser loginUser,
        @RequestBody OrderV1Dto.PlaceOrderRequest request
    ) {
        OrderInfo info = orderFacade.placeOrder(loginUser.id(), request.toCommand());
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(info));
    }

    @GetMapping
    @Override
    public ApiResponse<List<OrderV1Dto.MyOrderSummary>> getMyOrders(
        @AuthenticatedUser LoginUser loginUser,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        List<OrderV1Dto.MyOrderSummary> summaries = orderFacade.getMyOrders(loginUser.id(), from, to).stream()
            .map(OrderV1Dto.MyOrderSummary::from)
            .toList();
        return ApiResponse.success(summaries);
    }

    @GetMapping("/{orderId}")
    @Override
    public ApiResponse<OrderV1Dto.MyOrderDetail> getMyOrder(
        @AuthenticatedUser LoginUser loginUser,
        @PathVariable("orderId") Long orderId
    ) {
        OrderInfo info = orderFacade.getMyOrder(loginUser.id(), orderId);
        return ApiResponse.success(OrderV1Dto.MyOrderDetail.from(info));
    }
}
