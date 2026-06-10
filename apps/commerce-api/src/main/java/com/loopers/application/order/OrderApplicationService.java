package com.loopers.application.order;

import com.loopers.domain.brand.model.Brand;
import com.loopers.domain.brand.repository.BrandRepository;
import com.loopers.domain.coupon.model.CouponTemplate;
import com.loopers.domain.coupon.model.IssuedCoupon;
import com.loopers.domain.coupon.repository.CouponTemplateRepository;
import com.loopers.domain.coupon.repository.IssuedCouponRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
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
    private final IssuedCouponRepository issuedCouponRepository;
    private final CouponTemplateRepository couponTemplateRepository;

    @Transactional
    public Long  createOrder(String loginId, List<OrderItemRequest> items, Long issuedCouponId) {
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

        // 6. 원금 계산
        long originalAmount = items.stream()
            .mapToLong(item -> productMap.get(item.productId()).getPrice() * item.quantity())
            .sum();

        // 7. 쿠폰 검증 및 사용 처리 (비관적 락)
        long discountAmount = 0L;
        Long appliedCouponId = null;

        if (issuedCouponId != null) {
            IssuedCoupon issuedCoupon = issuedCouponRepository.findById(issuedCouponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다."));

            if (!issuedCoupon.getMemberId().equals(member.getId())) {
                throw new CoreException(ErrorType.FORBIDDEN, "본인 소유의 쿠폰이 아닙니다.");
            }

            CouponTemplate template = couponTemplateRepository.findById(issuedCoupon.getCouponTemplateId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 정보를 찾을 수 없습니다."));

            if (template.getExpiredAt().isBefore(ZonedDateTime.now())) {
                throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
            }

            discountAmount = template.calculateDiscount(originalAmount);
            issuedCoupon.use();

            appliedCouponId = issuedCouponId;
        }

        // 8. 주문 저장
        Order savedOrder = orderRepository.save(Order.create(member.getId(), originalAmount, discountAmount, appliedCouponId));

        // 9. 주문 항목 저장
        List<OrderItem> orderItems = items.stream()
            .map(item -> OrderItem.create(savedOrder.getId(), item.productId(), item.quantity()))
            .toList();
        List<OrderItem> savedItems = orderItemRepository.saveAll(orderItems);

        // 10. 스냅샷 저장
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

    @Transactional(readOnly = true)
    public Page<OrderSummary> getOrders(String loginId, ZonedDateTime startAt, ZonedDateTime endAt, int page, int size) {
        Member member = memberService.getMember(loginId);
        Page<Order> orders = orderRepository.findAllByMemberId(member.getId(), startAt, endAt, PageRequest.of(page, size));
        return orders.map(OrderSummary::from);
    }

    @Transactional(readOnly = true)
    public OrderDetail getOrder(String loginId, Long orderId) {
        Member member = memberService.getMember(loginId);
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 주문입니다."));

        if (!order.getMemberId().equals(member.getId())) {
            throw new CoreException(ErrorType.FORBIDDEN, "접근 권한이 없습니다.");
        }

        List<OrderItem> orderItems = orderItemRepository.findAllByOrderId(orderId);
        List<Long> itemIds = orderItems.stream().map(OrderItem::getId).toList();
        Map<Long, OrderItemSnapshot> snapshotMap = orderItemSnapshotRepository.findAllByOrderItemIdIn(itemIds).stream()
            .collect(Collectors.toMap(OrderItemSnapshot::getOrderItemId, s -> s));

        List<OrderDetail.OrderItemInfo> itemInfos = orderItems.stream()
            .map(item -> {
                OrderItemSnapshot snapshot = snapshotMap.get(item.getId());
                return new OrderDetail.OrderItemInfo(
                    snapshot.getProductName(),
                    snapshot.getBrandName(),
                    snapshot.getPrice(),
                    item.getQuantity(),
                    item.getStatus()
                );
            })
            .toList();

        return new OrderDetail(
            order.getId(),
            order.getOriginalAmount(),
            order.getDiscountAmount(),
            order.getTotalAmount(),
            order.getCreatedAt(),
            itemInfos
        );
    }
}
