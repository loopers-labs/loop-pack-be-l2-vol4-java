package com.loopers.infrastructure.product;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.product.ProductInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 상품 조회 캐시 저장소 (Redis, RedisTemplate 직접 제어).
 *
 * <p><strong>캐시 흐름을 명시적으로 다루기 위해 @Cacheable(AOP) 대신 RedisTemplate 를 직접 사용</strong>한다.
 * 값은 Jackson 으로 JSON 직렬화하여 String 으로 저장한다.
 *
 * <p><strong>캐시 장애 격리</strong>: 모든 캐시 연산은 try-catch 로 감싸 Redis 장애 시에도
 * 서비스가 죽지 않도록 한다. 조회 실패는 "캐시 미스"로 간주해 DB 로 폴백하고, 저장/삭제 실패는 무시한다.
 *
 * <p><strong>무효화 전략</strong>:
 * <ul>
 *   <li>상세({@code product:detail:{id}}) — 단일 키라 직접 evict (상품 수정/삭제, 재고 표시 변동 시)</li>
 *   <li>목록({@code product:list:{brandId}:{sort}:{page}:{size}}) — 키 조합이 많아 개별 evict 불가.
 *       상품 추가/삭제/가격변경 시 SCAN 패턴 삭제로 전체 무효화(A안). 버전키 방식 대비 키 수가 적어 SCAN 부담이 작고 직관적.</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class ProductCacheStore {

    private static final String DETAIL_PREFIX = "product:detail:";
    private static final String LIST_PREFIX = "product:list:";
    private static final Duration DETAIL_TTL = Duration.ofMinutes(10);
    private static final Duration LIST_TTL = Duration.ofMinutes(3);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    // ===== 상세 =====

    public Optional<ProductInfo> getDetail(Long productId) {
        try {
            String json = redisTemplate.opsForValue().get(detailKey(productId));
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, ProductInfo.class));
        } catch (Exception e) {
            log.warn("상품 상세 캐시 조회 실패 — DB 폴백. productId: {}", productId, e);
            return Optional.empty();
        }
    }

    public void putDetail(Long productId, ProductInfo info) {
        try {
            redisTemplate.opsForValue().set(detailKey(productId), objectMapper.writeValueAsString(info), DETAIL_TTL);
        } catch (Exception e) {
            log.warn("상품 상세 캐시 저장 실패 — 무시. productId: {}", productId, e);
        }
    }

    public void evictDetail(Long productId) {
        try {
            redisTemplate.delete(detailKey(productId));
        } catch (Exception e) {
            log.warn("상품 상세 캐시 삭제 실패 — 무시. productId: {}", productId, e);
        }
    }

    // ===== 목록 =====

    public Optional<List<ProductInfo>> getList(Long brandId, String sort, int page, int size) {
        try {
            String json = redisTemplate.opsForValue().get(listKey(brandId, sort, page, size));
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, new TypeReference<List<ProductInfo>>() {}));
        } catch (Exception e) {
            log.warn("상품 목록 캐시 조회 실패 — DB 폴백.", e);
            return Optional.empty();
        }
    }

    public void putList(Long brandId, String sort, int page, int size, List<ProductInfo> infos) {
        try {
            redisTemplate.opsForValue().set(listKey(brandId, sort, page, size), objectMapper.writeValueAsString(infos), LIST_TTL);
        } catch (Exception e) {
            log.warn("상품 목록 캐시 저장 실패 — 무시.", e);
        }
    }

    /** 목록 캐시 전체 무효화 (A안 — 상품 추가/삭제/가격변경 시). SCAN 으로 패턴 삭제. */
    public void evictAllList() {
        try {
            ScanOptions options = ScanOptions.scanOptions().match(LIST_PREFIX + "*").count(200).build();
            List<String> keys = new ArrayList<>();
            try (Cursor<String> cursor = redisTemplate.scan(options)) {
                while (cursor.hasNext()) {
                    keys.add(cursor.next());
                }
            }
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("상품 목록 캐시 전체 삭제 실패 — 무시.", e);
        }
    }

    private String detailKey(Long productId) {
        return DETAIL_PREFIX + productId;
    }

    private String listKey(Long brandId, String sort, int page, int size) {
        // brandId 미지정(전체) 은 "all" 로 키에 명시
        return LIST_PREFIX + (brandId == null ? "all" : brandId) + ":" + sort + ":" + page + ":" + size;
    }
}
