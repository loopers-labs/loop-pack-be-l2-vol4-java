package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.AuthenticatedUser;
import com.loopers.interfaces.auth.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
public class OrderV1Controller {

    private final OrderFacade orderFacade;

    @PostMapping
    public ApiResponse<OrderV1Dto.OrderResponse> createOrder(
        @LoginUser AuthenticatedUser user,
        @Valid @RequestBody OrderV1Dto.CreateOrderRequest request
    ) {
        OrderInfo info = orderFacade.createOrder(user.loginId(), request.toCommands());
        OrderV1Dto.OrderResponse response = OrderV1Dto.OrderResponse.from(info);
        return ApiResponse.success(response);
    }
}
