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
        validateCommand(command);

        Map<Long, Product> products = getProducts(command.productIds());
        Map<Long, Brand> brands = getBrands(products.values());
        Map<Long, ProductStock> productStocks = getProductStocksForUpdate(command.productIds());

        List<OrderItem> orderItems = command.items().stream()
            .map(item -> createOrderItem(item, products, brands, productStocks))
            .toList();

        Order order = orderService.createOrder(command.userId(), orderItems);
        return OrderInfo.from(order);
    }

    private void validateCommand(CreateOrderCommand command) {
        if (command == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 요청은 비어있을 수 없습니다.");
        }
        if (command.userId() == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 비어있을 수 없습니다.");
        }
        if (command.items().isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }

        HashSet<Long> productIds = new HashSet<>();
        for (CreateOrderCommand.Item item : command.items()) {
            validateItem(item);
            if (!productIds.add(item.productId())) {
                throw new CoreException(ErrorType.BAD_REQUEST, "같은 상품은 중복 주문할 수 없습니다.");
            }
        }
    }

    private void validateItem(CreateOrderCommand.Item item) {
        if (item == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 비어있을 수 없습니다.");
        }
        if (item.productId() == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 비어있을 수 없습니다.");
        }
        if (item.quantity() <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 수량은 1 이상이어야 합니다.");
        }
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
        Map<Long, Brand> brands,
        Map<Long, ProductStock> productStocks
    ) {
        Product product = products.get(item.productId());
        Brand brand = brands.get(product.getBrandId());
        ProductStock productStock = productStocks.get(item.productId());

        productStock.deduct(item.quantity());

        return OrderItem.create(
            brand.getId(),
            brand.getName().value(),
            product.getId(),
            product.getName(),
            product.getPrice(),
            item.quantity()
        );
    }
}
