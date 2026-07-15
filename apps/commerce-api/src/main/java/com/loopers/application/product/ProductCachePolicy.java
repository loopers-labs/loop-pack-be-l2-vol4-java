package com.loopers.application.product;

import com.loopers.domain.product.ProductSearchCondition;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 상품 캐시의 키 네이밍 / TTL / 캐시 대상 정책.
 *
 * <ul>
 *   <li>상세 — 변경 시 즉시 무효화하므로 TTL 을 길게 두고, 동시 만료 스파이크를 막기 위해 지터를 더한다.
 *   <li>목록 — like_count 변동이 잦고 키 조합이 많으므로 짧은 TTL 로 자연 수렴시키고, 조회가 집중되는 앞쪽 페이지만
 *       캐시해 키 공간을 줄인다.
 *   <li>키의 v1 세그먼트는 응답 스키마 변경 시 일괄 무효화 수단이다.
 * </ul>
 */
public final class ProductCachePolicy {

    private static final String DETAIL_KEY_PREFIX = "product:v1:detail:";
    private static final String LIST_KEY_PREFIX = "product:v1:list:";
    private static final Duration DETAIL_BASE_TTL = Duration.ofMinutes(10);
    private static final double DETAIL_TTL_JITTER_RATIO = 0.1;
    private static final int LIST_CACHEABLE_PAGE_LIMIT = 5;

    public static final Duration LIST_TTL = Duration.ofSeconds(60);

    private ProductCachePolicy() {}

    public static String detailKey(Long productId) {
        return DETAIL_KEY_PREFIX + productId;
    }

    public static String listKey(ProductSearchCondition condition) {
        String brand = condition.brandId() == null ? "all" : condition.brandId().toString();
        return LIST_KEY_PREFIX
            + brand + ":"
            + condition.sortType().name().toLowerCase() + ":"
            + condition.page() + ":"
            + condition.size();
    }

    public static Duration detailTtl() {
        long baseSeconds = DETAIL_BASE_TTL.toSeconds();
        long jitterBound = (long) (baseSeconds * DETAIL_TTL_JITTER_RATIO);
        long jitter = ThreadLocalRandom.current().nextLong(-jitterBound, jitterBound + 1);
        return Duration.ofSeconds(baseSeconds + jitter);
    }

    public static boolean isListCacheable(ProductSearchCondition condition) {
        return condition.page() < LIST_CACHEABLE_PAGE_LIMIT;
    }
}
