package com.loopers.application.order;

import com.loopers.domain.brand.model.Brand;
import com.loopers.domain.brand.repository.BrandRepository;
import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.service.MemberService;
import com.loopers.domain.order.model.Order;
import com.loopers.domain.order.model.OrderItem;
import com.loopers.domain.order.model.OrderItemSnapshot;
import com.loopers.domain.order.repository.OrderItemRepository;
import com.loopers.domain.order.repository.OrderItemSnapshotRepository;
import com.loopers.domain.order.repository.OrderRepository;
import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.repository.ProductRepository;
import com.loopers.domain.stock.repository.StockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class OrderApplicationService {

    private final MemberService memberService;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final StockRepository stockRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderItemSnapshotRepository orderItemSnapshotRepository;

    @Transactional
    public Long createOrder(String loginId, List<OrderItemRequest> items) {
        // 1. 회원 조회
        Member member = memberService.getMember(loginId);

        // 2. 중복 productId 검증
        List<Long> productIds = items.stream().map(OrderItemRequest::productId).toList();
        long distinctCount = productIds.stream().distinct().count();
        if (distinctCount != productIds.size()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "중복된 상품이 포함되어 있습니다.");
        }

        // 3. 상품 조회 및 존재 검증
        List<Product> products = productRepository.findAllByIdIn(productIds);
        if (products.size() != productIds.size()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "존재하지 않는 상품이 포함되어 있습니다.");
        }

        // 4. 브랜드 조회 (스냅샷용)
        List<Long> brandIds = products.stream().map(Product::getBrandId).distinct().toList();
        Map<Long, String> brandNameMap = brandRepository.findAllByIdIn(brandIds).stream()
            .collect(Collectors.toMap(Brand::getId, Brand::getName));

        // 5. 재고 차감 (원자적 UPDATE)
        Map<Long, Product> productMap = products.stream()
            .collect(Collectors.toMap(Product::getId, p -> p));
        Map<Long, Integer> quantityMap = items.stream()
            .collect(Collectors.toMap(OrderItemRequest::productId, OrderItemRequest::quantity));

        List<Long> outOfStockProductIds = new ArrayList<>();
        for (OrderItemRequest item : items) {
            int affected = stockRepository.deductStock(item.productId(), item.quantity());
            if (affected == 0) {
                outOfStockProductIds.add(item.productId());
            }
        }
        if (!outOfStockProductIds.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "재고가 부족한 상품이 있습니다: " + outOfStockProductIds);
        }

        // 6. 총 금액 계산
        long totalAmount = items.stream()
            .mapToLong(item -> productMap.get(item.productId()).getPrice() * item.quantity())
            .sum();

        // 7. 주문 저장
        Order savedOrder = orderRepository.save(Order.create(member.getId(), totalAmount));

        // 8. 주문 항목 저장
        List<OrderItem> orderItems = items.stream()
            .map(item -> OrderItem.create(savedOrder.getId(), item.productId(), item.quantity()))
            .toList();
        List<OrderItem> savedItems = orderItemRepository.saveAll(orderItems);

        // 9. 스냅샷 저장
        List<OrderItemSnapshot> snapshots = savedItems.stream()
            .map(orderItem -> {
                Product product = productMap.get(orderItem.getProductId());
                String brandName = brandNameMap.getOrDefault(product.getBrandId(), "");
                return OrderItemSnapshot.create(
                    orderItem.getId(), product.getName(), brandName, product.getPrice()
                );
            })
            .toList();
        orderItemSnapshotRepository.saveAll(snapshots);

        return savedOrder.getId();
    }
}
