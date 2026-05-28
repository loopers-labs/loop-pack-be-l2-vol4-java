package com.loopers.infrastructure.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, Long> {
    Optional<OrderJpaEntity> findByIdAndDeletedAtIsNull(Long id);
    Page<OrderJpaEntity> findAllByUserIdAndDeletedAtIsNull(Long userId, Pageable pageable);
}
