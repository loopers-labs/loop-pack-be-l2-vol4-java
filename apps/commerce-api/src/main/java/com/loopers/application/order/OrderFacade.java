package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.DiscountResult;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderLines;
import com.loopers.domain.order.OrderPeriod;
import com.loopers.domain.order.OrderResult;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.stock.StockService;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OrderFacade {

    private final UserService userService;
    private final ProductService productService;
    private final BrandService brandService;
    private final OrderService orderService;
    private final StockService stockService;
    private final CouponService couponService;

    @Transactional
    public OrderInfo placeOrder(Long userId, OrderCommand.Place command) {
        userService.getUser(userId);

        List<OrderLine> lines = command.items().stream()
            .map(item -> {
                ProductModel product = productService.getActive(item.productId());
                BrandModel brand = brandService.getActive(product.getBrandId());
                return OrderLine.snapshotOf(product, brand, item.quantity());
            })
            .toList();

        long totalAmount = OrderLines.of(lines).totalAmount();
        DiscountResult discount = couponService.apply(userId, command.couponId(), totalAmount);

        OrderResult result = orderService.create(userId, lines, discount.amount(), discount.usedCouponId());

        result.items().forEach(item -> stockService.decrease(item.getProductId(), item.getQuantity()));

        return OrderInfo.from(result.order(), result.items());
    }

    @Transactional(readOnly = true)
    public List<OrderInfo> getMyOrders(Long userId, LocalDate from, LocalDate to) {
        OrderPeriod period = OrderPeriod.of(from, to);
        return orderService.findMine(userId, period).stream()
            .map(result -> OrderInfo.from(result.order(), result.items()))
            .toList();
    }

    @Transactional(readOnly = true)
    public OrderInfo getMyOrder(Long userId, Long orderId) {
        OrderResult result = orderService.findOneOwnedBy(userId, orderId);
        return OrderInfo.from(result.order(), result.items());
    }

    @Transactional(readOnly = true)
    public Page<AdminOrderInfo> getAllOrders(int page, int size) {
        Page<OrderResult> orders = orderService.findAll(page, size);
        List<Long> userIds = orders.getContent().stream()
            .map(result -> result.order().getUserId())
            .distinct()
            .toList();
        Map<Long, UserModel> buyersById = userService.getAllByIdIn(userIds).stream()
            .collect(Collectors.toMap(UserModel::getId, Function.identity()));
        return orders.map(result ->
            AdminOrderInfo.from(result.order(), result.items(), buyersById.get(result.order().getUserId())));
    }

    @Transactional(readOnly = true)
    public AdminOrderInfo getOrder(Long orderId) {
        OrderResult result = orderService.findOne(orderId);
        UserModel buyer = userService.getUser(result.order().getUserId());
        return AdminOrderInfo.from(result.order(), result.items(), buyer);
    }
}
