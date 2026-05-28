package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderItemRequest;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
public class OrderV1Controller {

    private final OrderFacade orderFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderV1Dto.CreateResponse> createOrder(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestBody OrderV1Dto.CreateRequest request
    ) {
        List<OrderItemRequest> items = request.items().stream()
            .map(item -> new OrderItemRequest(item.productId(), item.quantity()))
            .toList();
        Long orderId = orderFacade.createOrder(loginId, items);
        return ApiResponse.success(new OrderV1Dto.CreateResponse(orderId));
    }
}
