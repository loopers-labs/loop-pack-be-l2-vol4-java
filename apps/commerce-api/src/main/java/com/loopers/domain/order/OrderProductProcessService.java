package com.loopers.domain.order;

import com.loopers.domain.product.Product;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
        Map<Long, Product> productsById = productsById(products);

        List<OrderLine> orderLines = new ArrayList<>();
        List<OrderFailure> failures = new ArrayList<>();

        for (OrderProductCommand command : commands) {
            tryOrderProduct(command, productsById, orderLines, failures);
        }

        if (orderLines.isEmpty()) {
            throw new CoreException(ErrorType.CONFLICT, "주문 가능한 상품이 없습니다.");
        }

        Order order = new Order(userLoginId, orderLines);
        return new OrderResult(order, List.copyOf(failures));
    }

    private void validateCommands(List<OrderProductCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 요청 상품은 1개 이상이어야 합니다.");
        }
    }

    private void tryOrderProduct(
        OrderProductCommand command,
        Map<Long, Product> productsById,
        List<OrderLine> orderLines,
        List<OrderFailure> failures
    ) {
        try {
            Product product = productsById.get(command.productId());
            if (product == null) {
                throw new CoreException(ErrorType.NOT_FOUND, "[id = " + command.productId() + "] 상품을 찾을 수 없습니다.");
            }
            product.deductStock(command.quantity());
            orderLines.add(new OrderLine(
                command.productId(),
                product.getName(),
                product.getPrice(),
                command.quantity()
            ));
        } catch (CoreException e) {
            failures.add(new OrderFailure(command.productId(), command.quantity(), e.getErrorType(), e.getMessage()));
        }
    }

    private Map<Long, Product> productsById(List<Product> products) {
        return products.stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));
    }
}
