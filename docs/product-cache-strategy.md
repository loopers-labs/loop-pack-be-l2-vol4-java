# 상품 캐시 전략 (Redis Cache-Aside)

## 배경 및 문제

상품 상세 조회와 상품 목록 조회는 읽기 빈도가 높고 데이터 변경 빈도는 낮다.  
매 요청마다 DB를 조회하면 다음 문제가 발생한다.

- 동일 상품에 대한 반복 조회 → 불필요한 DB I/O
- 상품 목록 조회 시 정렬·필터·페이징 조합에 따른 쿼리 부하
- 트래픽이 집중되면 DB 커넥션 풀 고갈 위험

## 해결 방향

**Cache-Aside 패턴으로 Redis를 읽기 캐시로 활용한다.**

- 조회 시 Redis를 먼저 확인 → 캐시 히트 시 DB 접근 없이 즉시 반환
- 캐시 미스 시 DB에서 조회 후 Redis에 저장
- 상품 데이터 변경(생성/수정/삭제) 시 관련 캐시 즉시 삭제(Evict)
- Redis 장애 또는 역직렬화 실패 시 DB 폴백으로 서비스 가용성 유지

## 흐름

### 조회 (Cache-Aside Read)

```
[GET /products/{id}] or [GET /products]
    │
    ▼
ProductService.getProduct() / getProducts()
    │  productCacheRepository.findById() → Redis 조회
    │
    ├─ 캐시 히트 ──────────────────────────────→ 반환
    │
    └─ 캐시 미스
           │
           ▼
       productRepository.find() → DB 조회
           │
           ▼
       productCacheRepository.save() → Redis 저장 (TTL 적용)
           │
           ▼
       반환
```

### 쓰기 (Cache Eviction)

```
[POST /products] / [PUT /products/{id}] / [DELETE /products/{id}]
    │
    ▼
ProductService.createProduct() / updateProduct() / deleteProduct()
    │  DB 변경 완료
    │
    ├─ productCacheRepository.evict(id)    ← 상세 캐시 삭제
    └─ productCacheRepository.evictAll()   ← 목록 캐시 전체 삭제
```

## Redis 키 구조

| 키 | TTL | 의미 |
|---|---|---|
| `product:cache:detail:{productId}` | 10분 | 상품 단건 조회 결과 |
| `product:cache:list:{sort}:{brandId}:{page}:{size}` | 5분 | 상품 목록 조회 결과 |

### 목록 키 예시

| 조건 | 키 |
|---|---|
| 전체 최신순 1페이지 | `product:cache:list:LATEST:all:0:20` |
| 브랜드 1 가격순 2페이지 | `product:cache:list:PRICE_ASC:1:1:20` |
| 전체 좋아요순 1페이지 | `product:cache:list:LIKES_DESC:all:0:20` |

- `brandId`가 없으면 `all` 사용
- sort 3종류(LATEST, PRICE_ASC, LIKES_DESC)를 각각 별도 키로 캐싱

## TTL 설계 근거

| 키 | TTL | 근거 |
|---|---|---|
| 상세 | 10분 | 상품 정보 변경 빈도 낮음. 수정 시 즉시 evict로 정합성 보장 |
| 목록 | 5분 | `LIKES_DESC` 정렬 기준인 `likeCount`가 배치 동기화 주기(5분)마다 갱신되므로 TTL을 맞춤 |

## 무효화 전략

| 이벤트 | 삭제 대상 |
|---|---|
| 상품 생성 | 목록 캐시 전체 (`product:cache:list:*`) |
| 상품 수정 | 상세 캐시 1건 + 목록 캐시 전체 |
| 상품 삭제 | 상세 캐시 1건 + 목록 캐시 전체 |

목록 캐시는 sort·brandId·page·size 조합이 다양하므로 개별 삭제 대신 패턴 전체 삭제를 선택했다.

## 캐시 미스 처리

Redis 장애 또는 역직렬화 오류 발생 시:

- `warn` 레벨 로그 기록
- `Optional.empty()` 반환 → DB 폴백 자동 수행
- 서비스는 정상 응답 유지

```java
try {
    return Optional.of(objectMapper.readValue(json, ProductCacheDto.class).toDomain());
} catch (Exception e) {
    log.warn("상품 상세 캐시 역직렬화 실패, productId={}", productId, e);
    return Optional.empty(); // DB 폴백
}
```

## 트레이드오프

| 항목 | 내용 |
|---|---|
| 일관성 | 수정/삭제 시 evict로 즉시 반영. TTL 만료 전 Redis 장애 복구 시 stale 데이터 유입 가능 |
| 목록 stale 위험 | `likeCount` 변경은 배치 동기화 이후 반영되므로, TTL 내에서는 캐시된 순위가 실제와 다를 수 있음 |
| 쓰기 트랜잭션과 evict | DB 커밋 성공 후 evict가 실행되므로, 커밋과 evict 사이의 짧은 구간에 다른 스레드가 stale 데이터를 캐싱할 수 있음 |
| 목록 캐시 전체 삭제 | `keys()` 명령으로 패턴 삭제 → 키 수가 많으면 Redis 응답 지연 가능. 키 수가 적은 현재 구조에서는 허용 범위 |
| 메모리 | 목록 페이지 조합 수에 비례해 Redis 메모리 사용. TTL로 자동 회수 |

## 성능 측정 결과 (k6)

### 테스트 환경

- 상품 100,000건 (소프트 삭제 30% 포함), 브랜드 50개
- VUs: 50, 각 시나리오 20초
- 인프라: Testcontainers (MySQL 8.0, Redis latest), 로컬 환경

### 시나리오

| 시나리오 | 설명 | ID 접근 방식 |
|---|---|---|
| cold_start | 캐시 미스 — 매 요청이 DB 조회 | 5,000개 ID 범위 분산 접근 |
| warm_cache | 캐시 히트 — Redis에서 즉시 반환 | 20개 ID 고정 반복 접근 |

warm_cache는 cold_start 종료 5초 후 시작 (TTL 만료 방지).

### 결과

| 지표 | 캐시 미스 (DB 조회) | 캐시 히트 (Redis) |
|---|---|---|
| p50 | 6.37ms | 1.80ms |
| p95 | 14.14ms | 5.65ms |
| p95 개선 | — | **약 2.5배 빠름** |

### 임계값 검증

| 임계값 | 기준 | 결과 |
|---|---|---|
| `duration_cache_miss` p(95) | < 2,000ms | 14.14ms ✓ |
| `duration_cache_hit` p(95) | < 100ms | 5.65ms ✓ |
| `error_rate` (5xx 기준) | < 1% | 0% ✓ |

> 404 응답(소프트 삭제 상품)은 정상 동작이므로 `error_rate`에서 제외하고 별도 `not_found_count` 메트릭으로 집계.

## 관련 파일

| 역할 | 파일 |
|---|---|
| 도메인 인터페이스 | `domain/product/ProductCacheRepository.java` |
| Redis 구현체 | `infrastructure/product/ProductCacheRepositoryImpl.java` |
| 직렬화 DTO | `infrastructure/product/ProductCacheDto.java` |
| 목록 직렬화 DTO | `infrastructure/product/ProductPageCacheDto.java` |
| 적용 서비스 | `application/product/ProductService.java` |
| 통합 테스트 | `application/product/ProductCacheIntegrationTest.java` |
| 성능 테스트 (Java) | `interfaces/api/ProductCachePerformanceE2ETest.java` |
| 성능 테스트 (k6) | `k6/product-cache.js` |