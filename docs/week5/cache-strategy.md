# 5주차 — Redis 캐시 전략

## 1. 패턴 선택: Look-aside (Cache-Aside)

```
[Client] → [Facade]
            ├─ GET cache → HIT → return
            └─ MISS → [DB] → SET cache → return
```

**선택 이유**
- DB 가 진리(source of truth) 로 유지된다 → 캐시 장애 시 fallback 으로 정상 동작
- Write-through / Write-behind 대비 구현 단순. 학습 목적 우선
- 부트캠프 진도상 일관성 모델이 명확한 패턴 선호

## 2. 키 설계

| 키 | 값 | TTL |
|---|---|---|
| `product:detail:{productId}` | `ProductDetailInfo` JSON | 5분 |
| `product:list:{brandId|all}:{sortType}` | `List<ProductInfo>` JSON | 1분 |

- 네임스페이스 prefix `product:` 로 도메인 격리
- 목록 키는 `(brandId, sortType)` 두 차원의 곱 — 현 단계 카디널리티 ≈ 50 × 3 + 3 = 153 키
- brandId 가 null (전체 조회) 인 경우 `all` 리터럴 사용 — Redis 패턴 매칭에서 일관

## 3. TTL 정책

| 캐시 | TTL | 근거 |
|------|------|------|
| 상세 | 5분 | 같은 사용자가 같은 상품을 반복 조회하는 단기 세션 + like 변동 빈도 절충 |
| 목록 | 1분 | 다수 사용자 공유 페이지, stale tolerance 낮음. 신규 상품/가격 변경 반영 지연 최대 1분 |

**왜 detail 이 list 보다 긴가?**
- 상세는 *특정 productId 1개* — like/order 시 surgical evict 가능
- 목록은 *다수 행 집합* — invalidate 비용 크고 (SCAN+DEL), 적중률은 낮으므로 TTL 자체를 줄여 자연 만료에 의존

## 4. 무효화 전략 (Hybrid: TTL + 명시 evict)

| 트리거 | 행동 |
|--------|------|
| `LikeFacade.like/unlike` | `evictDetail(productId)` |
| `OrderFacade.placeOrder` (재고 차감) | 주문된 모든 상품 `evictDetail` |
| `ProductFacade.createProduct` | `evictListsByBrand(brandId)` + `evictListsByBrand(null)` (전체 스코프) |
| 기타 (가격/상태 변경 등) | TTL 자연 만료 (현 단계 mutation API 없음) |

### evictListsByBrand 구현

```java
String pattern = "product:list:" + (brandId ?: "all") + ":*";
SCAN MATCH pattern COUNT 100 → DEL [keys...]
```

- `KEYS` 가 아니라 `SCAN` 사용 (non-blocking)
- 카디널리티 작아 (정렬 종류 3개) SCAN 비용 무시 가능

## 5. 장애 격리 — Cache 실패가 서비스 실패가 되지 않도록

`ProductRedisCacheAdapter` 의 모든 메서드는 try/catch 로 예외를 흡수한다.

- `get*` 실패 → `Optional.empty()` 반환 → Facade 는 자연스럽게 DB fallback
- `put*` 실패 → 조용히 통과 → 다음 요청도 MISS 지만 DB 가 받아준다
- `evict*` 실패 → 조용히 통과 → 다음 TTL 만료까지 stale (수용 가능)

## 6. 의도적으로 *하지 않은* 것

| 미적용 | 이유 |
|--------|------|
| Spring Cache abstraction (`@Cacheable`) | 키/TTL/직렬화 정책을 어노테이션 안에 숨기면 의도가 흐려진다. 학습 단계에서는 명시적 코드 흐름이 더 명확 |
| `@TransactionalEventListener(AFTER_COMMIT)` | 현 evict 위치(Facade) 가 tx 종료 후라 functionally 동일. 추후 운영 단에서 안전망으로 검토 |
| Redis cluster slot 고려 | 단일 master 구성. 클러스터 도입 시 키에 hash tag (`{brandId}`) 적용 검토 |
| Cache stampede 방어 (mutex / probabilistic XFETCH) | 학습 부담 ↑. 실측 후 필요 시 추가 |

## 7. 디렉토리 매핑

```
application/product/
├─ ProductCachePort.java          (Port 인터페이스)
└─ ProductFacade.java             (Look-aside 흐름)

infrastructure/product/
└─ ProductRedisCacheAdapter.java  (Redis 어댑터, TTL/SCAN/직렬화)

application/like/LikeFacade.java  (evictDetail 호출)
application/order/OrderFacade.java (evictDetail 호출)
```

## 8. 테스트 커버리지

- `ProductFacadeCacheTest` — Facade 흐름 (HIT/MISS/EVICT) 단위
- `ProductRedisCacheAdapterTest` — 어댑터 단위 (Mockito): 키 포맷, TTL 값, SCAN+DEL, 장애 격리
- 캐시 통합 테스트 (실제 Redis): 본 PR 범위 외 — Docker 환경 의존 (4주차 testcontainers 이슈와 동일)
