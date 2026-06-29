package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.QOrderModel;
import com.loopers.domain.order.enums.OrderStatus;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository orderJpaRepository;
    private final JPAQueryFactory queryFactory;

    @Override
    public OrderModel save(OrderModel order) {
        return orderJpaRepository.save(order);
    }

    @Override
    public Optional<OrderModel> findById(Long id) {
        return orderJpaRepository.findById(id);
    }

    @Override
    public Optional<OrderModel> findByOrderNumber(String orderNumber) {
        return orderJpaRepository.findByOrderNumber(orderNumber);
    }

    @Override
    public Optional<OrderModel> findByOrderNumberAndUserId(String orderNumber, Long userId) {
        QOrderModel order = QOrderModel.orderModel;
        return Optional.ofNullable(
                queryFactory.selectFrom(order)
                        .where(order.orderNumber.eq(orderNumber).and(order.userId.eq(userId)))
                        .fetchOne()
        );
    }

    @Override
    public Optional<OrderModel> findByIdAndUserId(Long id, Long userId) {
        QOrderModel order = QOrderModel.orderModel;
        return Optional.ofNullable(
                queryFactory.selectFrom(order)
                        .where(order.id.eq(id).and(order.userId.eq(userId)))
                        .fetchOne()
        );
    }

    @Override
    public List<OrderModel> findAllByUserId(Long userId) {
        return orderJpaRepository.findAllByUserId(userId);
    }

    @Override
    public Page<OrderModel> findAll(Pageable pageable) {
        return orderJpaRepository.findAll(pageable);
    }

    @Override
    public boolean cancelIfRequested(Long id, Long userId) {
        QOrderModel order = QOrderModel.orderModel;
        return queryFactory
                .update(order)
                .set(order.status, OrderStatus.CANCELLED)
                .where(order.id.eq(id)
                        .and(order.userId.eq(userId))
                        .and(order.status.eq(OrderStatus.REQUESTED)))
                .execute() > 0;
    }

    @Override
    public List<OrderModel> findAllByUserIdWithDateRange(Long userId, ZonedDateTime startAt, ZonedDateTime endAt) {
        QOrderModel order = QOrderModel.orderModel;

        BooleanBuilder where = new BooleanBuilder();
        where.and(order.userId.eq(userId));
        if (startAt != null) {
            where.and(order.createdAt.goe(startAt));
        }
        if (endAt != null) {
            where.and(order.createdAt.loe(endAt));
        }

        return queryFactory
                .selectFrom(order)
                .leftJoin(order.items).fetchJoin()
                .where(where)
                .distinct()
                .orderBy(order.createdAt.desc())
                .fetch();
    }
}
