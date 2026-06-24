# Volume 5 — 조회 성능 개선 과제

## 과제 체크리스트

### 🔖 Index
- [ ] 상품 목록 API에서 brandId 기반 검색, 좋아요 순 정렬 처리
- [ ] 조회 필터/정렬 조건별 유즈케이스 분석 → 인덱스 적용 → 전/후 성능 비교

### ❤️ Structure
- [ ] 상품 목록/상세 조회 시 좋아요 수 조회 및 좋아요 순 정렬 가능하도록 구조 개선
- [ ] 좋아요 적용/해제 시 상품 좋아요 수 정상 동기화

### ⚡ Cache
- [ ] Redis 캐시 적용 (상품 상세 + 상품 목록 API)
- [ ] TTL 또는 캐시 무효화 전략 적용
- [ ] 캐시 미스 시 서비스 정상 동작

---

## 현황 (AS-IS)

| 항목 | 상태 | 비고 |
|------|------|------|
| `like_count` 비정규화 | ✅ 완료 | `ProductModel.likeCount` 컬럼 존재 |
| 좋아요 등록/취소 동기화 | ✅ 완료 | `ProductLikeService` 원자적 증감 |
| 좋아요순 정렬 API | ✅ 완료 | `ProductSortType.LIKES_DESC` |
| DB 인덱스 | ❌ 없음 | `@Table`에 인덱스 선언 없음 |
| Redis 캐시 | ❌ 없음 | 미적용 |
| Flyway 마이그레이션 | ❌ 없음 | `ddl-auto: create` (local/test) |

---

## 구현 계획

### STEP 1 — 데이터 시딩 (10만건 준비)

- 목적: EXPLAIN 분석 전 인덱스 효과 측정 가능한 데이터 볼륨 확보
- 방법: SQL 스크립트 (stored procedure로 bulk insert)
- 분포: brand_id 다양 (10~20개 브랜드), like_count 0~10000 랜덤, price 다양

### STEP 2 — 쿼리 패턴 분석 및 인덱스 설계

실제 발생하는 쿼리 4가지:

| 쿼리 | WHERE | ORDER BY |
|------|-------|----------|
| ① 전체 활성 + 최신순 | `deleted_at IS NULL` | `created_at DESC` |
| ② 전체 활성 + 좋아요순 | `deleted_at IS NULL` | `like_count DESC` |
| ③ 브랜드 + 최신순 | `brand_id=? AND deleted_at IS NULL` | `created_at DESC` |
| ④ 브랜드 + 좋아요순 | `brand_id=? AND deleted_at IS NULL` | `like_count DESC` |

추가할 인덱스 4개:

```sql
-- ① ② 용
idx_products_active_created  : (deleted_at, created_at DESC)
idx_products_active_likes    : (deleted_at, like_count DESC)

-- ③ ④ 용
idx_products_brand_created   : (brand_id, deleted_at, created_at DESC)
idx_products_brand_likes     : (brand_id, deleted_at, like_count DESC)
```

- 적용 방법: `ProductModel` `@Table(indexes = {...})` 어노테이션
- 비교: EXPLAIN BEFORE/AFTER, type/key/rows/Extra 비교

### STEP 3 — Redis 캐시 적용

캐시 대상:

| API | 캐시 키 | TTL | 무효화 |
|-----|---------|-----|--------|
| 상품 상세 `GET /api/v1/products/{id}` | `product:{id}` | 10분 | 상품 수정/삭제 시 |
| 상품 목록 `GET /api/v1/products` | `products:brand={brandId}:sort={sort}:page={page}:size={size}` | 5분 | TTL 만료 (목록은 evict 복잡) |

구현 위치:
- `ProductFacade.getActive()` — `@Cacheable("product")`
- `ProductFacade.getActiveList()` — `@Cacheable("products")`
- `ProductFacade.update()` / `delete()` — `@CacheEvict("product")`

캐시 미스 처리: Spring Cache 기본 동작 (miss → DB 조회 → 캐시 저장)

---

## 진행 순서

1. **[현재]** STEP 2 먼저 — 인덱스 설계 및 적용 (데이터 시딩 포함)
2. STEP 3 — Redis 캐시 도입

> Structure 체크리스트(❤️)는 이미 구현 완료 상태. 확인만 필요.
