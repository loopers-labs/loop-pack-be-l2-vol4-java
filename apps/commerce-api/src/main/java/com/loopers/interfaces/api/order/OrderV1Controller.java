package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderDetail;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderItemRequest;
import com.loopers.application.order.OrderSummary;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZonedDateTime;
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

    @GetMapping
    public ApiResponse<Page<OrderV1Dto.OrderSummaryResponse>> getOrders(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime startAt,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime endAt,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<OrderSummary> summaries = orderFacade.getOrders(loginId, startAt, endAt, page, size);
        return ApiResponse.success(summaries.map(OrderV1Dto.OrderSummaryResponse::from));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderV1Dto.OrderDetailResponse> getOrder(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @PathVariable Long orderId
    ) {
        OrderDetail detail = orderFacade.getOrder(loginId, orderId);
        return ApiResponse.success(OrderV1Dto.OrderDetailResponse.from(detail));
    }
}
