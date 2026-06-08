package com.loopers.application.order;

import com.loopers.domain.order.OrderItemCommand;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final UserService userService;
    private final ProductService productService;
    private final OrderService orderService;

    @Transactional
    public OrderInfo createOrder(String loginId, String loginPw, List<OrderRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }

        UserModel user = userService.getUser(loginId, loginPw);

        List<OrderItemCommand> itemCommands = new ArrayList<>();
        long totalPrice = 0L;

        for (OrderRequest req : requests) {
            ProductModel product = productService.getProduct(req.productId());
            productService.deductStock(req.productId(), req.quantity());
            itemCommands.add(new OrderItemCommand(
                product.getId(),
                product.getName(),
                product.getPrice(),
                req.quantity()
            ));
            totalPrice += product.getPrice() * req.quantity();
        }

        OrderModel order = orderService.createOrder(user.getId(), totalPrice, itemCommands);
        List<OrderItemModel> savedItems = orderService.getOrderItems(order.getId());

        return OrderInfo.of(order, savedItems.stream().map(OrderItemInfo::from).toList());
    }

    public record OrderRequest(Long productId, int quantity) {}
}
