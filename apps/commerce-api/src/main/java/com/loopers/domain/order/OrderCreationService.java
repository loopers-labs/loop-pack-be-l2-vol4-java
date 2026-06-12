package com.loopers.domain.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 주문 생성 도메인 서비스 — Product 조회, 재고 차감, Order Aggregate 조립을 협력시킨다.
 *
 * <h3>처리 순서</h3>
 * <ol>
 *   <li>입력 라인을 productId 오름차순으로 정렬 — 다중 상품 재고 차감 시 데드락 회피</li>
 *   <li>각 라인별로 상품 조회 + 스냅샷 추출 + 재고 차감</li>
 *   <li>OrderItem 들로 Order Aggregate 를 구성하고 저장 (status=PENDING)</li>
 * </ol>
 *
 * 결제 외부 호출은 본 주차에서 다루지 않으며, 후주차에서 트랜잭션 분리·보상 트랜잭션과 함께 도입된다.
 */
@RequiredArgsConstructor
@Component
public class OrderCreationService {

    private final ProductService productService;
    private final BrandService brandService;
    private final OrderRepository orderRepository;

    @Transactional
    public OrderModel create(Long userId, List<OrderLine> lines) {
        if (userId == null || userId <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID 는 양수여야 합니다.");
        }
        if (lines == null || lines.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1 개 이상이어야 합니다.");
        }

        List<OrderLine> sorted = new ArrayList<>(lines);
        sorted.sort(Comparator.comparing(OrderLine::productId));

        List<OrderItemModel> items = new ArrayList<>(sorted.size());
        for (OrderLine line : sorted) {
            ProductModel product = productService.getProduct(line.productId());
            BrandModel brand = brandService.getBrand(product.getBrandId());

            product.decreaseStock(line.quantity());

            items.add(new OrderItemModel(
                product.getId(),
                product.getName(),
                brand.getName(),
                product.getPrice(),
                product.getImageUrl(),
                line.quantity()
            ));
        }

        OrderModel order = new OrderModel(userId, items);
        return orderRepository.save(order);
    }
}
