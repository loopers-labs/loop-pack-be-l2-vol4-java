package com.loopers.domain.product;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 단위 테스트용 인메모리 ProductLikeStatRepository.
 */
public class FakeProductLikeStatRepository implements ProductLikeStatRepository {

    private final Map<Long, ProductLikeStat> store = new LinkedHashMap<>();

    @Override
    public ProductLikeStat save(ProductLikeStat stat) {
        store.put(stat.getProductId(), stat);
        return stat;
    }

    @Override
    public Optional<ProductLikeStat> find(Long productId) {
        return Optional.ofNullable(store.get(productId));
    }

    @Override
    public List<ProductLikeStat> findAllByProductIdIn(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        List<ProductLikeStat> result = new ArrayList<>();
        for (Long id : productIds) {
            ProductLikeStat stat = store.get(id);
            if (stat != null) {
                result.add(stat);
            }
        }
        return result;
    }

    @Override
    public void saveAll(List<ProductLikeStat> stats) {
        if (stats == null) return;
        stats.stream().filter(Objects::nonNull).forEach(this::save);
    }

    @Override
    public long count() {
        return store.size();
    }
}
