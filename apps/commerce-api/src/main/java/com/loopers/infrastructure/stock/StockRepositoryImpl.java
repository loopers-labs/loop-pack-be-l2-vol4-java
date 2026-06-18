package com.loopers.infrastructure.stock;

import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class StockRepositoryImpl implements StockRepository {

    private final StockJpaRepository stockJpaRepository;

    /**
     * 순수 도메인 ↔ JPA 엔티티 경계.
     * - 신규(id == null): INSERT.
     * - 기존(id != null): managed 엔티티를 로드해 quantity만 복사 → dirty checking UPDATE.
     *   같은 트랜잭션에서 findByProductIdForUpdate로 이미 로드된 엔티티는 1차 캐시에서 반환되어
     *   잠금(FOR UPDATE)·version이 유지된다 → 동시성 보장(쿠폰 save 패턴과 동일).
     */
    @Override
    public StockModel save(StockModel stock) {
        if (stock.getId() == null) {
            StockEntity saved = stockJpaRepository.save(StockEntityMapper.toEntity(stock));
            return StockEntityMapper.toDomain(saved);
        }
        StockEntity entity = stockJpaRepository.findById(stock.getId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + stock.getId() + "] 재고를 찾을 수 없습니다."));
        entity.applyState(stock.getQuantity());
        return StockEntityMapper.toDomain(stockJpaRepository.save(entity));
    }

    @Override
    public Optional<StockModel> findByProductId(Long productId) {
        return stockJpaRepository.findByProductId(productId).map(StockEntityMapper::toDomain);
    }

    @Override
    public Optional<StockModel> findByProductIdForUpdate(Long productId) {
        return stockJpaRepository.findByProductIdForUpdate(productId).map(StockEntityMapper::toDomain);
    }

    @Override
    public List<StockModel> findByProductIds(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        return stockJpaRepository.findByProductIdIn(productIds).stream()
                .map(StockEntityMapper::toDomain)
                .toList();
    }
}
