package com.loopers.order.application;

import com.loopers.brand.application.BrandService;
import com.loopers.brand.domain.BrandModel;
import com.loopers.member.application.MemberService;
import com.loopers.order.domain.OrderCreationService;
import com.loopers.order.domain.OrderLine;
import com.loopers.order.domain.OrderModel;
import com.loopers.product.application.ProductService;
import com.loopers.product.domain.ProductModel;
import com.loopers.support.PageSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
@Transactional
public class OrderFacade {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final MemberService memberService;
    private final ProductService productService;
    private final BrandService brandService;
    private final OrderCreationService orderCreationService = new OrderCreationService();
    private final OrderService orderService;

    public OrderInfo createOrder(Long memberId, List<OrderLine> lines) {
        memberService.get(memberId);

        List<Long> productIds = lines.stream().map(OrderLine::productId).distinct().toList();
        List<ProductModel> products = productService.getAllByIds(productIds);
        Map<Long, ProductModel> productMap =
            products.stream().collect(Collectors.toMap(ProductModel::getId, Function.identity()));

        List<Long> brandIds = products.stream().map(ProductModel::getBrandId).distinct().toList();
        Map<Long, BrandModel> brandMap = brandService.getMapByIds(brandIds);

        OrderModel order = orderCreationService.create(memberId, lines, productMap, brandMap);

        productService.saveAll(products);
        return OrderInfo.from(orderService.save(order));
    }

    /** 본인 주문 목록을 조회한다. startAt/endAt 이 주어지면 주문 생성일(Asia/Seoul) 기준으로 필터링한다. */
    @Transactional(readOnly = true)
    public List<OrderInfo> getMyOrders(Long memberId, LocalDate startAt, LocalDate endAt) {
        return orderService.getMyOrders(memberId).stream()
            .filter(order -> withinRange(order.getCreatedAt(), startAt, endAt))
            .map(OrderInfo::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public OrderInfo getMyOrder(Long memberId, Long orderId) {
        return OrderInfo.from(orderService.getForMember(memberId, orderId));
    }

    @Transactional(readOnly = true)
    public Page<OrderInfo> getAllOrders(int page, int size) {
        List<OrderInfo> infos =
            orderService.getAll().stream()
                .sorted(Comparator.comparing(OrderModel::getId).reversed())
                .map(OrderInfo::from)
                .toList();
        return PageSupport.paginate(infos, page, size);
    }

    @Transactional(readOnly = true)
    public OrderInfo getOrder(Long orderId) {
        return OrderInfo.from(orderService.get(orderId));
    }

    private static boolean withinRange(ZonedDateTime createdAt, LocalDate startAt, LocalDate endAt) {
        if (startAt == null && endAt == null) {
            return true;
        }
        if (createdAt == null) {
            // 생성일을 판단할 수 없으면 필터에서 제외하지 않는다.
            return true;
        }
        LocalDate orderedDate = createdAt.withZoneSameInstant(SEOUL).toLocalDate();
        if (startAt != null && orderedDate.isBefore(startAt)) {
            return false;
        }
        return endAt == null || !orderedDate.isAfter(endAt);
    }
}
