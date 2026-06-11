package com.loopers.application.order;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderItems;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class OrderItemFactory {

    private final ProductService productService;
    private final BrandService brandService;

    public OrderItems create(List<CreateOrderCommand.Item> items) {
        List<Product> products = productService.getAllByIds(productIds(items));
        Map<Long, Product> productsById = productsById(products);
        Map<Long, Brand> brandsById = brandsById(brandService.getAllByIds(brandIds(products)));

        return OrderItems.of(items.stream()
            .map(item -> toOrderItem(item, productsById, brandsById))
            .toList());
    }

    private List<Long> productIds(List<CreateOrderCommand.Item> items) {
        return items.stream()
            .map(CreateOrderCommand.Item::productId)
            .distinct()
            .toList();
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

    private OrderItem toOrderItem(
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
