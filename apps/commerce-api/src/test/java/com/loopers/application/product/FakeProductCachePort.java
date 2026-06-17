package com.loopers.application.product;

import com.loopers.domain.product.ProductSortType;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 단위 테스트용 in-memory 캐시 — TTL 없는 단순 Map.
 * HIT / MISS / EVICT 동작만 검증한다.
 */
public class FakeProductCachePort implements ProductCachePort {

    private final Map<Long, ProductDetailInfo> details = new HashMap<>();
    private final Map<String, List<ProductInfo>> lists = new HashMap<>();

    public int detailHits = 0;
    public int detailMisses = 0;
    public int detailEvicts = 0;
    public int listEvictsByBrand = 0;

    @Override
    public Optional<ProductDetailInfo> getDetail(Long productId) {
        ProductDetailInfo found = details.get(productId);
        if (found == null) {
            detailMisses++;
            return Optional.empty();
        }
        detailHits++;
        return Optional.of(found);
    }

    @Override
    public void putDetail(ProductDetailInfo info) {
        details.put(info.productId(), info);
    }

    @Override
    public void evictDetail(Long productId) {
        details.remove(productId);
        detailEvicts++;
    }

    @Override
    public Optional<List<ProductInfo>> getList(Long brandId, ProductSortType sortType) {
        return Optional.ofNullable(lists.get(listKey(brandId, sortType)));
    }

    @Override
    public void putList(Long brandId, ProductSortType sortType, List<ProductInfo> infos) {
        lists.put(listKey(brandId, sortType), infos);
    }

    @Override
    public void evictListsByBrand(Long brandId) {
        String brandPrefix = brandKeyPrefix(brandId);
        Iterator<String> it = lists.keySet().iterator();
        while (it.hasNext()) {
            if (it.next().startsWith(brandPrefix)) {
                it.remove();
            }
        }
        listEvictsByBrand++;
    }

    public boolean containsDetail(Long productId) {
        return details.containsKey(productId);
    }

    public boolean containsList(Long brandId, ProductSortType sortType) {
        return lists.containsKey(listKey(brandId, sortType));
    }

    private static String listKey(Long brandId, ProductSortType sortType) {
        return brandKeyPrefix(brandId) + sortType.name();
    }

    private static String brandKeyPrefix(Long brandId) {
        return (brandId == null ? "all" : brandId) + ":";
    }
}
