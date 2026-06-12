package com.loopers.domain.order;

import com.loopers.domain.product.Product;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class OrderProductProcessService {

    public OrderResult createOrder(
        String userLoginId,
        List<OrderProductCommand> commands,
        List<Product> products
    ) {
        validateCommands(commands);
        List<OrderProductCommand> mergedCommands = mergeCommands(commands);
        Map<Long, Product> productsById = productsById(products);
        validateAllProductsExist(mergedCommands, productsById);
        validateAllProductsOrderable(mergedCommands, productsById);

        List<OrderLine> orderLines = mergedCommands.stream()
            .map(command -> orderProduct(command, productsById.get(command.productId())))
            .toList();

        Order order = new Order(userLoginId, orderLines);
        return new OrderResult(order, List.of());
    }

    private void validateCommands(List<OrderProductCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 요청 상품은 1개 이상이어야 합니다.");
        }
        for (OrderProductCommand command : commands) {
            if (command.productId() == null) {
                throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 비어있을 수 없습니다.");
            }
            if (command.quantity() == null || command.quantity() < 1) {
                throw new CoreException(ErrorType.BAD_REQUEST, "주문 수량은 1 이상이어야 합니다.");
            }
        }
    }

    private List<OrderProductCommand> mergeCommands(List<OrderProductCommand> commands) {
        Map<Long, Integer> quantitiesByProductId = new LinkedHashMap<>();
        for (OrderProductCommand command : commands) {
            quantitiesByProductId.merge(command.productId(), command.quantity(), Integer::sum);
        }

        List<OrderProductCommand> mergedCommands = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : quantitiesByProductId.entrySet()) {
            mergedCommands.add(new OrderProductCommand(entry.getKey(), entry.getValue()));
        }
        return mergedCommands;
    }

    private void validateAllProductsExist(List<OrderProductCommand> commands, Map<Long, Product> productsById) {
        for (OrderProductCommand command : commands) {
            if (!productsById.containsKey(command.productId())) {
                throw new CoreException(ErrorType.NOT_FOUND, "[id = " + command.productId() + "] 상품을 찾을 수 없습니다.");
            }
        }
    }

    private void validateAllProductsOrderable(List<OrderProductCommand> commands, Map<Long, Product> productsById) {
        for (OrderProductCommand command : commands) {
            Product product = productsById.get(command.productId());
            if (product.getStock() < command.quantity()) {
                throw new CoreException(ErrorType.CONFLICT, "상품 재고가 부족합니다.");
            }
        }
    }

    private OrderLine orderProduct(OrderProductCommand command, Product product) {
        product.deductStock(command.quantity());
        return new OrderLine(
            command.productId(),
            product.getName(),
            product.getPrice(),
            command.quantity()
        );
    }

    private Map<Long, Product> productsById(List<Product> products) {
        return products.stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));
    }
}
