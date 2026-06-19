# like_count 비정규화 개선 구현 계획

## 개요

[설계 검토 문서](../analysis/like-count-denormalization-analysis.md)에서 도출한 두 가지 문제를 순차적으로 해결한다.

| 문제 | 원인 | 해결 단계 |
|---|---|---|
| 상품 수정 시 `like_count` lost update | `product.like_count`가 admin 수정 flush에 같이 덮임 | Phase 1: `product_stats` 분리 |
| 인기 상품 좋아요 API 응답속도 저하 | `like_count` 갱신이 요청 경로 안에서 동기 처리 | Phase 2: 비동기 Outbox |

두 단계는 독립 배포 가능하다. Phase 1만 적용해도 lost update는 해소되며, Phase 2는 그 위에 응답속도를 추가로 개선한다.

---

## 현재 구조 (As-Is)

```
LikeFacade.like()  ─ @Transactional ─┐
  ├─ likeService.register()           │  likes 테이블 INSERT
  └─ productService.increaseLikeCount()│  product.like_count 원자적 UPDATE
                                       │  + 인덱스 2개 재정렬
                                      ─┘

ProductService.update()
  1. productRepository.find(id)  ← like_count 포함 전체 로드
  2. product.update(name, price) ← 메모리에서 name/price만 변경
  3. productRepository.save()    ← 옛 like_count 값이 flush되어 덮어씀 (lost update)
```

`like_count`와 관련된 `ProductModel`의 인덱스:
```
idx_product_deleted_at_like_count           (deleted_at, like_count)
idx_product_brand_id_deleted_at_like_count  (brand_id, deleted_at, like_count)
```

---

## Phase 1: `product_stats` 집계 테이블 분리

**목표**: `like_count`를 `product` 테이블 외부로 격리해 admin 수정의 lost update를 원천 차단한다.  
**효과 범위**: lost update 해결. 응답속도는 이 단계에서 변하지 않는다.

### 읽기 경로 설계

`like_count`를 `product_stats`로 분리하면 기존 인덱스 2개가 무력화된다. 시나리오별로 드라이빙 테이블과 인덱스 전략을 다르게 가져간다.

**A3 (전체 + LIKES_DESC)**

`product_stats`를 드라이빙으로 삼아 `(deleted_at, like_count)` 인덱스를 활용한다.

```sql
SELECT p.*
FROM product_stats ps
JOIN product p ON ps.product_id = p.id
WHERE ps.deleted_at IS NULL
ORDER BY ps.like_count DESC
LIMIT 20;
```

```
(deleted_at, like_count) 인덱스
  → deleted_at IS NULL 구간으로 좁힘 (ref)
  → like_count DESC 순서로 이미 정렬 (Backward index scan)
  → LIMIT 20 조기 종료, filesort 없음
```

이를 위해 `product`가 soft delete될 때 `product_stats`도 함께 soft delete되어야 한다. `product_stats`가 자체적으로 삭제 여부를 알아야 드라이빙 테이블로서 완결성이 보장된다.

**B3 (브랜드 + LIKES_DESC)**

`brand_id`는 `product`에, `like_count`는 `product_stats`에 있어 단일 인덱스로 두 조건을 동시에 커버할 수 없다. `product`를 드라이빙으로 잡아 `(brand_id, deleted_at)` 인덱스로 대상을 1만건으로 줄인 뒤 filesort를 수용한다.

```sql
SELECT p.*
FROM product p
JOIN product_stats ps ON ps.product_id = p.id
WHERE p.brand_id = ? AND p.deleted_at IS NULL
ORDER BY ps.like_count DESC
LIMIT 20;
```

```
idx_product_brand_id_deleted_at 인덱스
  → brand_id + deleted_at 조건으로 ~1만건으로 선필터링
  → product_stats 조인
  → 1만건 like_count 기준 filesort
  → LIMIT 20
```

1만건 filesort는 MySQL 메모리 정렬로 처리 가능한 수준이다. 실제 모니터링에서 문제가 확인되면 그 시점에 Redis Sorted Set 도입을 검토한다.

**인덱스 변경 요약**

| 테이블 | 제거 | 추가 |
|---|---|---|
| `product` | `idx_product_deleted_at_like_count` | `idx_product_brand_id_deleted_at` |
| `product` | `idx_product_brand_id_deleted_at_like_count` | — |
| `product_stats` | — | `idx_product_stats_deleted_at_like_count` `(deleted_at, like_count)` |
| `product_stats` | — | `idx_product_stats_product_id` `(product_id)` |

### 구현 작업 목록

#### 1. `ProductStatsModel` 엔티티 생성

```
domain/product/ProductStatsModel.java
```

- 필드: `productId`, `likeCount`
- `@Table(name = "product_stats", indexes = { (deleted_at, like_count), (product_id) })`
- `BaseEntity` 상속 — `deletedAt` 관리는 BaseEntity의 `delete()` / `restore()`로 처리

#### 2. `ProductStatsRepository` 인터페이스

```
domain/product/ProductStatsRepository.java
```

```java
// 동시성 안전: 원자적 UPDATE로 race condition 없이 카운트 정합성 보장
void increaseLikeCount(Long productId);
void decreaseLikeCount(Long productId);
Optional<ProductStatsModel> findByProductId(Long productId);
Page<ProductStatsModel> findAllByDeletedAtIsNull(Pageable pageable);         // A3용
Page<ProductStatsModel> findAllByProductIdIn(List<Long> productIds, Pageable pageable); // B3용
```

#### 3. 인프라 구현

```
infrastructure/product/ProductStatsJpaRepository.java
infrastructure/product/ProductStatsRepositoryImpl.java
```

- `increaseLikeCount` / `decreaseLikeCount`: `@Modifying @Query("UPDATE ProductStatsModel SET likeCount = likeCount ± 1 WHERE productId = :productId")`
- A3 정렬 쿼리: `product_stats` 단독으로 `(deleted_at, like_count)` 인덱스 활용
- B3 정렬 쿼리: `product`를 드라이빙으로 조인 후 like_count 정렬

#### 4. `ProductStatsService` 도메인 서비스

```
domain/product/ProductStatsService.java
```

- `increaseLikeCount(Long productId)` → `@Transactional`
- `decreaseLikeCount(Long productId)` → `@Transactional`
- `getByProductId(Long productId)` → NOT_FOUND 예외
- `softDelete(Long productId)` — product soft delete 시 연동 호출
- `findPage(Pageable pageable)` — A3용
- `findPageByProductIds(List<Long> productIds, Pageable pageable)` — B3용

#### 5. `ProductModel`에서 `likeCount` 제거

- `ProductModel.likeCount` 필드 및 getter 제거
- 인덱스 변경:
  - `idx_product_deleted_at_like_count` 제거
  - `idx_product_brand_id_deleted_at_like_count` 제거
  - `idx_product_brand_id_deleted_at` 추가 — B3 선필터링용
- `ProductRepository.increaseLikeCount` / `decreaseLikeCount` 인터페이스에서 제거
- `ProductJpaRepository` / `ProductRepositoryImpl`에서 해당 구현 제거

#### 6. `ProductService.delete()` — soft delete 연동

`product`가 soft delete될 때 `product_stats`도 함께 처리해야 한다. `product_stats`가 드라이빙 테이블로서 완결성을 갖기 위한 전제 조건이다.

```java
// ProductService.delete() 또는 LikeFacade/ProductFacade 레벨에서
productStatsService.softDelete(productId);
```

#### 7. `LikeFacade` 변경

```java
// Before
productService.increaseLikeCount(product.getId());

// After
productStatsService.increaseLikeCount(product.getId());
```

#### 8. 읽기 경로 변경 (ProductInfo 조립)

`ProductInfo.likeCount`는 여전히 필요하다. `ProductInfoAssembler`가 `ProductStatsModel`도 수집하도록 확장한다.

```java
// ProductInfoAssembler.toInfoList()
Map<Long, ProductStatsModel> statsMap = productStatsService.getMapByProductIds(productIds);
// ProductInfo.from()에 stats 추가
product.getLikeCount() → statsMap.get(product.getId()).getLikeCount()
```

`ProductFacade.findProducts()`에서 like_count 기준 정렬 쿼리는 시나리오별로 분기한다.
- 브랜드 필터 없음(A3): `productStatsService.findPage()` 경유 — `product_stats` 드라이빙
- 브랜드 필터 있음(B3): `product` 드라이빙 조인 쿼리

#### 9. 데이터 이행 (백필)

Phase 1 배포 전, 기존 `product.like_count` 값을 `product_stats`로 복사한다.

```sql
INSERT INTO product_stats (product_id, like_count, created_at, updated_at)
SELECT p.id, p.like_count, NOW(), NOW()
FROM product p
ON DUPLICATE KEY UPDATE like_count = VALUES(like_count);
```

배포 순서:
1. 백필 SQL 실행 (무중단)
2. 애플리케이션 배포 (product_stats 읽기/쓰기 전환)
3. `product.like_count` 컬럼은 잠시 유지 후 별도 배포에서 제거

#### 10. 테스트 변경 대상

| 파일 | 변경 내용 |
|---|---|
| `LikeFacadeIntegrationTest` | given에 `ProductStatsModel` 초기 데이터 추가, `productStatsRepository`로 결과 검증 |
| `LikeConcurrencyIntegrationTest` | 검증 대상을 `product_stats.like_count`로 변경 |
| `ProductFacadeIntegrationTest` | like_count 정렬 시나리오 검증 경로 변경, soft delete 시 product_stats 연동 검증 추가 |
| `ProductModelTest` | `likeCount` 관련 테스트 케이스 제거 |
| `ProductStatsServiceTest` | 신규 단위 테스트 추가 |

---

## Phase 2: 비동기 Outbox 패턴

**목표**: `like_count` 갱신을 요청 경로 밖으로 꺼내 API 응답속도를 개선한다.  
**전제**: Phase 1 완료 후 적용.

```
Phase 2 흐름:

LikeFacade.like()  ─ @Transactional ─┐
  ├─ likeService.register()           │  likes 테이블 INSERT
  └─ likeOutboxService.record()       │  like_outbox 테이블 INSERT
                                      ─┘  ← API 즉시 반환

LikeOutboxProcessor (스케줄러, 별도 스레드)
  └─ 미처리 outbox 조회
       └─ productStatsService.increaseLikeCount() / decreaseLikeCount()
            └─ outbox 상태를 DONE으로 업데이트
```

### 구현 작업 목록

#### 1. `LikeOutboxModel` 엔티티

```
domain/like/LikeOutboxModel.java
```

- 필드: `productId`, `delta`(+1/-1), `status`(PENDING/DONE/FAILED)
- `BaseEntity` 상속

#### 2. `LikeOutboxRepository` 인터페이스 + 인프라 구현

```
domain/like/LikeOutboxRepository.java
infrastructure/like/LikeOutboxJpaRepository.java
infrastructure/like/LikeOutboxRepositoryImpl.java
```

- `findAllByStatus(OutboxStatus status)` — 배치 처리용

#### 3. `LikeOutboxService`

```
domain/like/LikeOutboxService.java
```

- `record(Long productId, int delta)` — PENDING 레코드 저장
- `findPending()` — 미처리 목록 조회

#### 4. `LikeFacade` 변경

```java
// Before (Phase 1)
if (likeService.register(...).isApplied()) {
    productStatsService.increaseLikeCount(product.getId());
}

// After (Phase 2)
if (likeService.register(...).isApplied()) {
    likeOutboxService.record(product.getId(), +1);
}
```

#### 5. `LikeOutboxProcessor` 스케줄러

```
application/like/LikeOutboxProcessor.java
```

- `@Scheduled(fixedDelay = 1000)` 또는 Batch Job
- PENDING outbox 조회 → `productStatsService.increase/decreaseLikeCount()` → 상태 DONE 업데이트
- at-least-once 보장: outbox 레코드는 DONE으로 마킹된 이후에만 삭제(또는 유지)
- 중복 처리 안전: `likes` 테이블의 `uk_likes_user_product` 유니크 제약이 멱등성을 뒷받침

#### 6. 테스트 추가

| 파일 | 내용 |
|---|---|
| `LikeFacadeIntegrationTest` | like 후 outbox PENDING 레코드 생성 확인 |
| `LikeOutboxProcessorIntegrationTest` | 프로세서 실행 후 `product_stats.like_count` 반영 및 outbox DONE 전환 확인 |

---

## 향후 고려 (조건부)

비동기 경로가 도입된 후에도 인기 상품의 `product_stats.like_count` 갱신이 hot row 문제로 실제 DB 부하가 된다는 것이 모니터링으로 확인되면, Kafka 도입을 검토한다.

- 같은 `product_id`의 이벤트를 모아 배치 반영 → DB 쓰기 횟수 감소
- consumer 처리 속도에 상한 → DB 부하 조절

B3 (브랜드 + LIKES_DESC) 정렬 성능이 실제 문제가 되면 Redis Sorted Set으로 브랜드별 like_count 순 목록을 캐싱하는 방안을 검토한다.

현 시점에서는 두 항목 모두 구현 범위에 포함하지 않는다.

---

## 정리

```
Phase 1 (product_stats 분리)
  └─ lost update 해결
  └─ 읽기 경로:
       A3 (전체 + LIKES_DESC): product_stats 드라이빙, (deleted_at, like_count) 인덱스, filesort 없음
       B3 (브랜드 + LIKES_DESC): product 드라이빙, (brand_id, deleted_at) 선필터링, 1만건 filesort 수용
  └─ product soft delete 시 product_stats도 함께 soft delete
  └─ 단독 배포 가능

Phase 2 (비동기 Outbox)
  └─ 응답속도 해결 (like_count 갱신을 요청 경로 밖으로)
  └─ at-least-once 보장
  └─ Phase 1 이후 적용
```
