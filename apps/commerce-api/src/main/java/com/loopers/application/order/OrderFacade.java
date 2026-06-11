package com.loopers.application.order;

import com.loopers.domain.order.OrderItemData;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.stock.StockService;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final UserService userService;
    private final ProductService productService;
    private final StockService stockService;
    private final OrderService orderService;

    public record OrderItemDto(Long productId, Long quantity) {
    }

    @Transactional
    public OrderInfo createOrder(String loginId, String loginPw, List<OrderItemDto> orderItems) {
        UserModel user = userService.getLoginUser(loginId, loginPw);

        List<Long> productIds = orderItems.stream().map(OrderItemDto::productId).toList();

        Map<Long, ProductModel> productMap = productService.findAllByIdsOrThrow(productIds).stream()
                .collect(Collectors.toMap(ProductModel::getId, p -> p));

        orderItems.forEach(cmd -> stockService.decreaseStock(cmd.productId(), cmd.quantity()));

        List<OrderItemData> itemDataList = orderItems.stream()
                .map(cmd -> {
                    ProductModel product = productMap.get(cmd.productId());
                    return new OrderItemData(product.getId(), product.getName(), product.getPrice(), cmd.quantity());
                })
                .toList();

        OrderModel saved = orderService.create(user.getId(), itemDataList);
        return OrderInfo.from(saved);
    }

    @Transactional(readOnly = true)
    public List<OrderInfo> getOrders(String loginId, String loginPw, LocalDate startAt, LocalDate endAt) {
        UserModel user = userService.getLoginUser(loginId, loginPw);

        return orderService.getOrdersByUserIdBetween(user.getId(), startAt, endAt).stream()
                .map(OrderInfo::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderInfo getOrder(String loginId, String loginPw, Long orderId) {
        UserModel user = userService.getLoginUser(loginId, loginPw);

        OrderModel order = orderService.getById(orderId);
        order.validateOwner(user.getId());

        return OrderInfo.from(order);
    }
}
