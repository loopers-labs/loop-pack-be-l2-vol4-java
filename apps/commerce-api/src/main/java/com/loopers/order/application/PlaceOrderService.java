package com.loopers.order.application;

import com.loopers.brand.application.BrandReader;
import com.loopers.common.domain.Money;
import com.loopers.coupon.application.CouponUsageService;
import com.loopers.order.domain.Order;
import com.loopers.order.domain.OrderItem;
import com.loopers.order.domain.OrderItemRepository;
import com.loopers.order.domain.OrderRepository;
import com.loopers.order.domain.ShippingDestination;
import com.loopers.product.application.ProductInfo;
import com.loopers.product.application.ProductReader;
import com.loopers.product.domain.ProductStock;
import com.loopers.product.domain.ProductStockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.product.domain.ProductErrorCode;
import com.loopers.user.application.UserReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class PlaceOrderService {

    private final UserReader userReader;
    private final ProductReader productReader;
    private final ProductStockRepository productStockRepository;
    private final BrandReader brandReader;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CouponUsageService couponUsageService;

    @Transactional
    public OrderResult.Detail createPendingOrder(OrderCommand.Create command, String orderNumber) {
        log.info("주문 생성 시작 userId={} itemCount={} userCouponId={}",
                command.userId(), command.items().size(), command.userCouponId());
        // 주문자가 존재하지 않으면 재고 차감 전에 NOT_FOUND 로 종료한다.
        userReader.ensureExists(command.userId());

        // 재고 락은 productId 오름차순으로 획득해 동시 주문 deadlock 을 피한다.
        List<OrderCommand.Line> sortedLines = command.items().stream()
                .sorted(Comparator.comparing(OrderCommand.Line::productId))
                .toList();

        List<OrderItem> orderItems = new ArrayList<>();
        for (OrderCommand.Line line : sortedLines) {
            // getInfo 는 ON_SALE·미삭제 상품만 반환하므로 삭제·판매중지(SUSPENDED) 상품 주문을 함께 막는다.
            ProductInfo product = productReader.getInfo(line.productId());
            ProductStock stock = productStockRepository.findByProductIdForUpdate(line.productId())
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, ProductErrorCode.STOCK_NOT_FOUND));
            stock.decrease(line.quantity());

            String brandName = brandReader.getName(product.brandId());
            orderItems.add(OrderItem.create(
                    line.productId(), product.name(), product.brandId(), brandName,
                    product.price(), line.quantity()
            ));
        }

        ShippingDestination shipping = ShippingDestination.create(
                command.recipientName(), command.recipientPhone(), command.zipcode(),
                command.address1(), command.address2()
        );
        Order order = Order.create(command.userId(), orderNumber, shipping, orderItems);
        applyCoupon(command, order);
        Order saved = orderRepository.save(order);

        orderItems.forEach(item -> {
            item.assignOrder(saved.getId());
            orderItemRepository.save(item);
        });

        log.info("PENDING 주문 저장 orderId={} orderNumber={} total={} discount={} final={}",
                saved.getId(), saved.getOrderNumber(), saved.getTotalAmount().value(),
                saved.getDiscountAmount().value(), saved.getFinalAmount().value());
        return OrderResult.Detail.of(saved, orderItems);
    }

    private void applyCoupon(OrderCommand.Create command, Order order) {
        if (command.userCouponId() == null) {
            return;
        }
        Money discount = couponUsageService.use(
                command.userCouponId(), command.userId(), order.getTotalAmount().value());
        order.applyDiscount(command.userCouponId(), discount);
    }
}
