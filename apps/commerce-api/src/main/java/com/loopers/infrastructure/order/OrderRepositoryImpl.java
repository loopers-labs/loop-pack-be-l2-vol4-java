package com.loopers.infrastructure.order;

import com.loopers.domain.common.PageResult;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderCommand;
import com.loopers.domain.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class OrderRepositoryImpl implements OrderRepository {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final OrderJpaRepository orderJpaRepository;
    private final OrderMapper orderMapper;

    @Override
    public Order save(Order order) {
        OrderJpaEntity entity = orderMapper.toJpaEntity(order);
        OrderJpaEntity saved = orderJpaRepository.save(entity);
        return orderMapper.toDomain(saved);
    }

    @Override
    public Optional<Order> find(Long id) {
        return orderJpaRepository.findById(id).map(orderMapper::toDomain);
    }

    @Override
    public PageResult<Order> findAllByUser(OrderCommand.MySearch search) {
        ZonedDateTime from = search.from() == null ? null : search.from().atStartOfDay(ZONE);
        ZonedDateTime to = search.to() == null ? null : search.to().plusDays(1).atStartOfDay(ZONE);
        Pageable pageable = PageRequest.of(search.page(), search.size());
        Page<OrderJpaEntity> page = orderJpaRepository.searchByUser(search.userId(), from, to, pageable);
        return toPageResult(page, search.page(), search.size());
    }

    @Override
    public PageResult<Order> findAll(OrderCommand.AdminSearch search) {
        Pageable pageable = PageRequest.of(search.page(), search.size(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<OrderJpaEntity> page = orderJpaRepository.findAll(pageable);
        return toPageResult(page, search.page(), search.size());
    }

    private PageResult<Order> toPageResult(Page<OrderJpaEntity> page, int reqPage, int reqSize) {
        List<Order> content = page.getContent().stream()
                .map(orderMapper::toDomain)
                .toList();
        return new PageResult<>(content, reqPage, reqSize, page.hasNext(), page.getTotalElements());
    }
}
