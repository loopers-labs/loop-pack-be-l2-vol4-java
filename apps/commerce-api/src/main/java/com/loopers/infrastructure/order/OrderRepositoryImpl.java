package com.loopers.infrastructure.order;
import com.loopers.domain.order.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Optional;
@Component @RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepository { private final OrderJpaRepository jpa; public Optional<Order> find(long id){return jpa.findById(id);} public Order save(Order o){return jpa.save(o);} }
