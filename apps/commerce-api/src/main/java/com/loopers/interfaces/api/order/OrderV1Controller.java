package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.vo.ShippingInfo;
import com.loopers.interfaces.api.common.interceptor.AuthInterceptor;
import com.loopers.interfaces.api.common.response.ApiResponse;
import com.loopers.interfaces.api.common.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
public class OrderV1Controller implements OrderV1ApiSpec {

    private final OrderFacade orderFacade;

    @PostMapping
    @Override
    public ApiResponse<OrderV1Dto.OrderResponse> create(
        @RequestBody @Valid OrderV1Dto.CreateRequest request,
        @RequestAttribute(AuthInterceptor.AUTHENTICATED_USER) UserModel user
    ) {
        OrderV1Dto.ShippingInfoRequest s = request.shippingInfo();
        ShippingInfo shippingInfo = new ShippingInfo(s.receiverName(), s.receiverPhone(), s.zipCode(), s.address(), s.detailAddress());
        List<OrderFacade.OrderItemRequest> items = request.items().stream()
            .map(i -> new OrderFacade.OrderItemRequest(i.productId(), i.quantity()))
            .toList();
        OrderInfo info = orderFacade.create(user.getId(), shippingInfo, items, request.couponId());
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(info));
    }

    @GetMapping("/{orderId}")
    @Override
    public ApiResponse<OrderV1Dto.OrderResponse> get(
        @PathVariable UUID orderId,
        @RequestAttribute(AuthInterceptor.AUTHENTICATED_USER) UserModel user
    ) {
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(orderFacade.get(orderId, user)));
    }

    @GetMapping
    @Override
    public ApiResponse<PageResponse<OrderV1Dto.OrderResponse>> getList(
        @RequestParam UUID userId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime startAt,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime endAt,
        Pageable pageable,
        @RequestAttribute(AuthInterceptor.AUTHENTICATED_USER) UserModel user
    ) {
        Page<OrderInfo> page = orderFacade.getListByUser(userId, user, startAt, endAt, pageable);
        return ApiResponse.success(PageResponse.from(page.map(OrderV1Dto.OrderResponse::from)));
    }

    @PostMapping("/{orderId}/cancel")
    @Override
    public ApiResponse<OrderV1Dto.OrderResponse> cancel(
        @PathVariable UUID orderId,
        @RequestAttribute(AuthInterceptor.AUTHENTICATED_USER) UserModel user
    ) {
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(orderFacade.cancel(orderId, user)));
    }
}
