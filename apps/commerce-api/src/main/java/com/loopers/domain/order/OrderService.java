package com.loopers.domain.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.page.PagePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductService productService;
    private final BrandService brandService;

    /**
     * 주문 생성(PENDING) — 각 상품의 활성 검증 + 스냅샷 + 재고 차감을 한 트랜잭션으로.
     * 재고 부족 시 CONFLICT로 전체 롤백 (한 줄이라도 실패하면 차감 전부 원복).
     * 결제(PG)는 이 트랜잭션 밖에서 별도 호출한다 (01 §7.6).
     */
    @Transactional
    public OrderModel placeOrderPending(Long userId, PaymentMethod method, List<OrderLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목이 비어있습니다.");
        }
        OrderModel order = new OrderModel(userId, method);
        for (OrderLine line : lines) {
            ProductModel product = productService.getActiveProduct(line.productId());
            BrandModel brand = brandService.getActiveBrand(product.getBrandId());
            productService.deductStock(product.getId(), line.quantity());
            order.addItem(new OrderItem(
                    product.getId(),
                    product.getName(),
                    brand.getName(),
                    product.getImageUrl(),
                    new Money(product.getPrice()),
                    line.quantity()
            ));
        }
        order.calculateTotals();
        return orderRepository.save(order);
    }

    @Transactional
    public OrderModel markPaid(Long orderId) {
        OrderModel order = getOrder(orderId);
        order.markPaid();
        return orderRepository.save(order);
    }

    /** 결제 실패 처리 — 상태 전이 + 항목별 재고 원복 (01 §7.6). */
    @Transactional
    public OrderModel markFailed(Long orderId, String reason) {
        OrderModel order = getOrder(orderId);
        order.markFailed(reason);
        for (OrderItem item : order.getItems()) {
            productService.restoreStock(item.getProductId(), item.getQuantity());
        }
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public OrderModel getOrder(Long orderId) {
        return orderRepository.find(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + orderId + "] 주문을 찾을 수 없습니다."));
    }

    /** 본인 주문 목록 — 최신순 페이지 조회 (UC-09). */
    @Transactional(readOnly = true)
    public List<OrderModel> getMyOrders(Long userId, int page, int size) {
        PagePolicy.validate(page, size);
        return orderRepository.findByUserId(userId, page, size);
    }
}
