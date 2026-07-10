package com.loopers.application.order;

import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponTemplateService;
import com.loopers.domain.coupon.IssuedCouponModel;
import com.loopers.domain.coupon.IssuedCouponService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
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
    private final IssuedCouponService issuedCouponService;
    private final CouponTemplateService couponTemplateService;

    public record OrderItemDto(Long productId, Long quantity) {
    }

    @Transactional
    public OrderInfo createOrder(String loginId, String loginPw, List<OrderItemDto> orderItems, Long issuedCouponId) {
        UserModel user = userService.getLoginUser(loginId, loginPw);

        List<Long> productIds = orderItems.stream().map(OrderItemDto::productId).toList();

        Map<Long, ProductModel> productMap = productService.findAllByIdsOrThrow(productIds).stream()
                .collect(Collectors.toMap(ProductModel::getId, p -> p));

        List<OrderItemData> itemDataList = orderItems.stream()
                .map(cmd -> {
                    ProductModel product = productMap.get(cmd.productId());
                    return new OrderItemData(product.getId(), product.getName(), product.getPrice(), cmd.quantity());
                })
                .toList();

        BigDecimal originalPrice = itemDataList.stream()
                .map(d -> d.productPrice().multiply(BigDecimal.valueOf(d.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal discountAmount = BigDecimal.ZERO;
        if (issuedCouponId != null) {
            IssuedCouponModel issued = issuedCouponService.getMyIssuedCoupon(issuedCouponId, user.getId());
            CouponTemplateModel template = couponTemplateService.getById(issued.getCouponTemplateId());
            template.validateApplicability(originalPrice);
            issuedCouponService.use(issued.getId());
            discountAmount = template.calculateDiscountAmount(originalPrice);
        }

        orderItems.stream()
                .sorted(Comparator.comparingLong(OrderItemDto::productId))
                .forEach(cmd -> stockService.decreaseStock(cmd.productId(), cmd.quantity()));

        OrderModel saved = orderService.create(user.getId(), itemDataList, discountAmount);

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

        OrderModel order = orderService.getByIdAndValidateOwner(orderId, user.getId());

        return OrderInfo.from(order);
    }
}
