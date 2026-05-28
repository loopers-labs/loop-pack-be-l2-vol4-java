package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderResult;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.stock.StockService;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OrderFacade {

    private final UserService userService;
    private final ProductService productService;
    private final BrandService brandService;
    private final OrderService orderService;
    private final StockService stockService;

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

        OrderResult result = orderService.create(userId, lines);

        result.items().forEach(item -> stockService.decrease(item.getProductId(), item.getQuantity()));

        return OrderInfo.from(result.order(), result.items());
    }
}
