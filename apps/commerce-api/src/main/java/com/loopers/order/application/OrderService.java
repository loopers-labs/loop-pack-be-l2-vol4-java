package com.loopers.order.application;

import com.loopers.brand.application.BrandReader;
import com.loopers.brand.domain.Brand;
import com.loopers.order.domain.Order;
import com.loopers.order.domain.OrderItem;
import com.loopers.order.domain.OrderItemRepository;
import com.loopers.order.domain.OrderRepository;
import com.loopers.order.domain.ShippingDestination;
import com.loopers.product.application.ProductReader;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductStock;
import com.loopers.product.domain.ProductStockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderService {

    private final ProductReader productReader;
    private final ProductStockRepository productStockRepository;
    private final BrandReader brandReader;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderNumberGenerator orderNumberGenerator;
    private final PaymentService paymentService;

    @Transactional
    public OrderResult.Detail create(OrderCommand.Create command) {
        // 재고 락은 productId 오름차순으로 획득해 동시 주문 deadlock 을 피한다.
        List<OrderCommand.Line> sortedLines = command.items().stream()
                .sorted(Comparator.comparing(OrderCommand.Line::productId))
                .toList();

        List<OrderItem> orderItems = new ArrayList<>();
        for (OrderCommand.Line line : sortedLines) {
            // getActive 는 ON_SALE·미삭제 상품만 반환하므로 삭제·판매중지(SUSPENDED) 상품 주문을 함께 막는다.
            Product product = productReader.getActive(line.productId());
            ProductStock stock = productStockRepository.findByProductIdForUpdate(line.productId())
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품 재고를 찾을 수 없습니다."));
            stock.decrease(line.quantity());

            Brand brand = brandReader.get(product.getBrandId());
            orderItems.add(OrderItem.create(
                    line.productId(), product.getName(), product.getBrandId(), brand.getName(),
                    product.getPrice(), line.quantity()
            ));
        }

        String orderNumber = orderNumberGenerator.generate();
        ShippingDestination shipping = ShippingDestination.create(
                command.recipientName(), command.recipientPhone(), command.zipcode(),
                command.address1(), command.address2()
        );
        Order order = Order.create(command.userId(), orderNumber, shipping, command.paymentMethod(), orderItems);
        Order saved = orderRepository.save(order);

        orderItems.forEach(item -> {
            item.assignOrder(saved.getId());
            orderItemRepository.save(item);
        });

        // 결제 통합 지점. 현재 범위에서는 stub 이라 상태 전이 없이 PENDING 으로 종료된다.
        paymentService.pay(saved);

        return OrderResult.Detail.of(saved, orderItems);
    }

    @Transactional(readOnly = true)
    public List<OrderResult.Summary> getMyOrders(Long userId) {
        return orderRepository.findByUserId(userId).stream()
                .map(OrderResult.Summary::from)
                .toList();
    }
}
