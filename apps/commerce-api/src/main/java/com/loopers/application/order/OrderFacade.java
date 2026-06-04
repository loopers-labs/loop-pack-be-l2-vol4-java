package com.loopers.application.order;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponUseCommand;
import com.loopers.domain.coupon.vo.CouponDiscount;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderItems;
import com.loopers.domain.order.OrderSearchPeriod;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.vo.OrderPayment;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.stock.ProductStockService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.pagination.PageQuery;
import com.loopers.support.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final ProductService productService;
    private final BrandService brandService;
    private final ProductStockService productStockService;
    private final CouponService couponService;
    private final OrderService orderService;

    @Transactional
    public OrderInfo createOrder(CreateOrderCommand command) {
        ZonedDateTime orderedAt = ZonedDateTime.now();

        OrderItems orderItems = createOrderItems(command);
        CouponUseCommand couponUseCommand = command.couponUseCommand(orderItems.calculateTotalPrice(), orderedAt);
        CouponDiscount discount = couponService.applyToOrder(couponUseCommand);
        OrderPayment payment = OrderPayment.withDiscount(
            discount.orderAmount().value(),
            discount.discountAmount().value()
        );

        productStockService.deduct(command.stockDeductions());

        Order order = Order.create(command.userId(), orderItems, command.userCouponId(), payment);
        return OrderInfo.from(orderService.saveOrder(order));
    }

    @Transactional(readOnly = true)
    public PageResult<OrderInfo> getMyOrders(GetMyOrdersCommand command) {
        return orderService.getOrders(
                command.userId(),
                new PageQuery(command.page(), command.size()),
                OrderSearchPeriod.of(command.startAt(), command.endAt())
            )
            .map(OrderInfo::from);
    }

    @Transactional(readOnly = true)
    public OrderInfo getMyOrderDetail(Long orderId, Long userId) {
        Order order = orderService.getOrder(orderId);
        if (!order.isOrderedBy(userId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "다른 사용자의 주문은 조회할 수 없습니다.");
        }
        return OrderInfo.from(order);
    }

    private OrderItems createOrderItems(CreateOrderCommand command) {
        List<Long> productIds = command.requestedProductIds();

        List<Product> orderProducts = productService.getAllByIds(productIds);
        Map<Long, Product> productsById = productsById(orderProducts);

        List<Long> brandIds = brandIds(orderProducts);
        List<Brand> orderBrands = brandService.getAllByIds(brandIds);
        Map<Long, Brand> brandsById = brandsById(orderBrands);

        List<OrderItem> orderItems = command.items().stream()
            .map(item -> createOrderItem(item, productsById, brandsById))
            .toList();

        return OrderItems.of(orderItems);
    }

    private Map<Long, Product> productsById(List<Product> products) {
        return products.stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    private List<Long> brandIds(List<Product> products) {
        return products.stream()
            .map(Product::getBrandId)
            .distinct()
            .toList();
    }

    private Map<Long, Brand> brandsById(List<Brand> brands) {
        return brands.stream()
            .collect(Collectors.toMap(Brand::getId, Function.identity()));
    }

    private OrderItem createOrderItem(
        CreateOrderCommand.Item requestedItem,
        Map<Long, Product> productsById,
        Map<Long, Brand> brandsById
    ) {
        Product product = productsById.get(requestedItem.productId());
        Brand brand = brandsById.get(product.getBrandId());
        return OrderItem.create(
            brand.getId(),
            brand.getName().value(),
            product.getId(),
            product.getName(),
            product.getPrice(),
            requestedItem.quantity()
        );
    }
}
