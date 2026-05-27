package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthUser;
import com.loopers.interfaces.api.auth.LoginUser;
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
    public ApiResponse<OrderV1Dto.Response> placeOrder(
        @LoginUser AuthUser authUser,
        @Valid @RequestBody OrderV1Dto.CreateRequest request
    ) {
        OrderInfo info = orderFacade.placeOrder(authUser.id(), request.toCommands());
        return ApiResponse.success(OrderV1Dto.Response.from(info));
    }
}
