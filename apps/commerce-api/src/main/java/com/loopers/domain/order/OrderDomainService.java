package com.loopers.domain.order;

import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.inventory.Inventory;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class OrderDomainService {

    public Order create(Long userId,
                        List<Product> products,
                        List<Inventory> inventories,
                        List<OrderCommand.OrderLine> rawLines,
                        UserCoupon userCoupon,
                        ZonedDateTime now) {

        OrderLines lines = OrderLines.from(rawLines);
        Map<Long, Product> productById = (products == null ? List.<Product>of() : products).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, Inventory> inventoryByProductId = (inventories == null ? List.<Inventory>of() : inventories).stream()
                .collect(Collectors.toMap(Inventory::getProductId, Function.identity()));

        List<Long> missing = lines.productIds().stream()
                .filter(id -> !productById.containsKey(id))
                .toList();
        if (!missing.isEmpty()) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품: " + missing);
        }

        List<Long> missingInventory = lines.productIds().stream()
                .filter(id -> !inventoryByProductId.containsKey(id))
                .toList();
        if (!missingInventory.isEmpty()) {
            throw new CoreException(ErrorType.NOT_FOUND, "재고 정보가 없는 상품: " + missingInventory);
        }

        // 락 보유 구간 안에서도 한 번 더 방어(불변식 자기보호). 응용은 이 검증을 락 '전에' 먼저 호출해 fail-cheap 한다.
        if (userCoupon != null) {
            userCoupon.assertUsableBy(userId, now);
        }

        List<OrderItem> items = new ArrayList<>();
        for (Long productId : lines.productIds()) {
            Product product = productById.get(productId);
            Inventory inventory = inventoryByProductId.get(productId);
            int qty = lines.quantityOf(productId);
            inventory.decrease(qty);
            items.add(OrderItem.of(product.getId(), product.getName(), product.getPrice(), qty));
        }

        OrderItems orderItems = OrderItems.from(items);

        Money original = orderItems.totalAmount();
        Money discount = userCoupon == null
                ? Money.of(0)
                : userCoupon.calculateDiscount(original);

        return Order.create(userId, orderItems, discount);
    }
}
