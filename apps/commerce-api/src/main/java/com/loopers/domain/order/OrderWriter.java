package com.loopers.domain.order;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class OrderWriter {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final OrderProcessor orderProcessor;

    public OrderResult placeOrder(String userLoginId, List<OrderProductCommand> commands) {
        Map<Long, ProductModel> productsById = findProductsById(commands);
        OrderResult result = orderProcessor.createOrder(userLoginId, commands, productsById);
        productsById.values().forEach(productRepository::save);
        return new OrderResult(orderRepository.save(result.order()), result.failures());
    }

    private Map<Long, ProductModel> findProductsById(List<OrderProductCommand> commands) {
        return commands.stream()
            .map(OrderProductCommand::productId)
            .distinct()
            .map(productRepository::find)
            .flatMap(Optional::stream)
            .collect(Collectors.toMap(ProductModel::getId, Function.identity()));
    }
}
