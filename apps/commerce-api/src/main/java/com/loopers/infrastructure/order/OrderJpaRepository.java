package com.loopers.infrastructure.order;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, Long> {
    Optional<OrderJpaEntity> findByIdAndUserLoginId(Long id, String userLoginId);

    List<OrderJpaEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<OrderJpaEntity> findAllByUserLoginIdOrderByCreatedAtDesc(String userLoginId);
}
