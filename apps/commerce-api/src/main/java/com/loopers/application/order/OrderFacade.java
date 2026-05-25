package com.loopers.application.order;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.stock.ProductStock;
import com.loopers.domain.stock.ProductStockService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.pagination.PageQuery;
import com.loopers.support.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
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
    private final OrderService orderService;

    @Transactional
    public OrderInfo createOrder(CreateOrderCommand command) {
        Map<Long, Product> products = getProducts(command.productIds());
        Map<Long, Brand> brands = getBrands(products.values());

        List<OrderItem> orderItems = command.items().stream()
            .map(item -> createOrderItem(item, products, brands))
            .toList();

        Order order = Order.create(command.userId(), orderItems);
        Map<Long, ProductStock> productStocks = getProductStocksForUpdate(command.productIds());
        deductStocks(command.items(), productStocks);

        Order savedOrder = orderService.saveOrder(order);
        return OrderInfo.from(savedOrder);
    }

    @Transactional(readOnly = true)
    public PageResult<OrderInfo> getMyOrders(GetMyOrdersCommand command) {
        return orderService.getOrders(
                command.userId(),
                new PageQuery(command.page(), command.size()),
                command.startAt(),
                command.endAt()
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

    private Map<Long, Product> getProducts(List<Long> productIds) {
        List<Product> products = productService.getProducts(productIds);
        if (products.size() != new HashSet<>(productIds).size()) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다.");
        }
        return products.stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    private Map<Long, Brand> getBrands(Collection<Product> products) {
        List<Long> brandIds = products.stream()
            .map(Product::getBrandId)
            .distinct()
            .toList();
        List<Brand> brands = brandService.getBrands(brandIds);
        if (brands.size() != brandIds.size()) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 브랜드의 상품은 주문할 수 없습니다.");
        }
        return brands.stream()
            .collect(Collectors.toMap(Brand::getId, Function.identity()));
    }

    private Map<Long, ProductStock> getProductStocksForUpdate(List<Long> productIds) {
        List<ProductStock> productStocks = productStockService.getProductStocksForUpdate(productIds);
        if (productStocks.size() != new HashSet<>(productIds).size()) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품 재고입니다.");
        }
        return productStocks.stream()
            .collect(Collectors.toMap(ProductStock::getProductId, Function.identity()));
    }

    private OrderItem createOrderItem(
        CreateOrderCommand.Item item,
        Map<Long, Product> products,
        Map<Long, Brand> brands
    ) {
        Product product = products.get(item.productId());
        Brand brand = brands.get(product.getBrandId());

        return OrderItem.create(
            brand.getId(),
            brand.getName().value(),
            product.getId(),
            product.getName(),
            product.getPrice(),
            item.quantity()
        );
    }

    private void deductStocks(List<CreateOrderCommand.Item> items, Map<Long, ProductStock> productStocks) {
        items.forEach(item -> {
            ProductStock productStock = productStocks.get(item.productId());
            productStock.deduct(item.quantity());
        });
    }
}
