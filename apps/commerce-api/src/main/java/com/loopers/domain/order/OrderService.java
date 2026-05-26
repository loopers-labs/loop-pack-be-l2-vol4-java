package com.loopers.domain.order;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductService productService;

    @Transactional
    public OrderResult createOrder(String userLoginId, List<OrderProductCommand> commands) {
        validateCommands(commands);

        List<OrderLineModel> orderLines = new ArrayList<>();
        List<ProductModel> orderedProducts = new ArrayList<>();
        List<OrderFailure> failures = new ArrayList<>();

        for (OrderProductCommand command : commands) {
            tryOrderProduct(command, orderLines, orderedProducts, failures);
        }

        if (orderLines.isEmpty()) {
            throw new CoreException(ErrorType.CONFLICT, "주문 가능한 상품이 없습니다.");
        }

        orderedProducts.forEach(productService::saveProduct);
        OrderModel order = orderRepository.save(new OrderModel(userLoginId, orderLines));
        return new OrderResult(order, List.copyOf(failures));
    }

    private void validateCommands(List<OrderProductCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 요청 상품은 1개 이상이어야 합니다.");
        }
    }

    private void tryOrderProduct(
        OrderProductCommand command,
        List<OrderLineModel> orderLines,
        List<ProductModel> orderedProducts,
        List<OrderFailure> failures
    ) {
        try {
            ProductModel product = productService.getProduct(command.productId());
            product.deductStock(command.quantity());
            orderLines.add(new OrderLineModel(
                command.productId(),
                product.getName(),
                product.getPrice(),
                command.quantity()
            ));
            orderedProducts.add(product);
        } catch (CoreException e) {
            failures.add(new OrderFailure(command.productId(), command.quantity(), e.getErrorType(), e.getMessage()));
        }
    }
}
