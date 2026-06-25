package com.loopers.order.infrastructure;

import com.loopers.order.domain.Order;
import com.loopers.order.domain.OrderRepository;
import com.loopers.shared.pagination.PageQuery;
import com.loopers.shared.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository orderJpaRepository;

    @Override
    public Order save(Order order) {
        return orderJpaRepository.save(order);
    }

    @Override
    public Optional<Order> findById(Long orderId) {
        return orderJpaRepository.findWithItemsById(orderId);
    }

    @Override
    public PageResult<Order> findAll(PageQuery query) {
        Page<Long> orderIds = orderJpaRepository.findIds(latestPageRequest(query));
        return toPageResult(orderIds);
    }

    @Override
    public PageResult<Order> findAllByUserId(Long userId, PageQuery query, ZonedDateTime startAt, ZonedDateTime endBefore) {
        Page<Long> orderIds = orderJpaRepository.findIdsByUserIdAndPeriod(
            userId,
            startAt,
            endBefore,
            latestPageRequest(query)
        );
        return toPageResult(orderIds);
    }

    private PageResult<Order> toPageResult(Page<Long> orderIds) {
        List<Order> orders = findAllByIdsKeepingPageOrder(orderIds.getContent());
        return PageResult.from(orderIds, orders);
    }

    private PageRequest latestPageRequest(PageQuery query) {
        return PageRequest.of(query.page(), query.size(), Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id")
        ));
    }

    private List<Order> findAllByIdsKeepingPageOrder(Collection<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Order> orders = orderJpaRepository.findAllWithItemsByIdIn(orderIds).stream()
            .collect(Collectors.toMap(Order::getId, Function.identity()));
        return orderIds.stream()
            .map(orders::get)
            .toList();
    }
}
