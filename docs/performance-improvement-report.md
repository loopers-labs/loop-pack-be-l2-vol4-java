# 조회 성능 개선 보고서 — Index / 비정규화 / Redis 캐시

## 1. 개요 및 문제 정의

상품 목록/상세 조회는 커머스에서 트래픽이 가장 집중되는 읽기 경로다. 개선 전 `commerce-api` 는 다음 상태였다.

### AS-IS

| 영역 | 상태 | 문제 |
| --- | --- | --- |
| 목록 API | `GET /api/v1/products` — 필터/정렬/페이징 없음, 전체 반환 | 10만 건 기준 응답 자체가 불가능한 수준. 브랜드 필터·좋아요순 정렬 요구를 충족 못 함 |
| 인덱스 | PK 외 없음 | 어떤 조건 조회도 풀스캔 + filesort |
| 좋아요 | 도메인 자체가 없음 | 좋아요 수 노출/정렬 불가. COUNT 집계로 만들면 정렬 시 상품 × 좋아요 조인·집계 폭발 |
| 캐시 | Redis 인프라만 있고 미사용 | 동일 조회가 매번 DB 도달 |

### TO-BE

| 영역 | 개선 | 근거 |
| --- | --- | --- |
| ① 목록 조회 | QueryDSL 기반 `brandId` 필터 + `likes_desc/latest` 정렬 + 페이징, 유즈케이스별 복합 인덱스 | 동등 조건 → 정렬 컬럼 순서의 인덱스로 filesort 제거, LIMIT 만큼만 소비 |
| ② 좋아요 구조 | `product.like_count` 비정규화 + 등록/취소 시 원자적 동기화 | 정렬 기준을 단일 테이블 컬럼으로 만들어 인덱스 정렬 가능하게 함 (vs MV 비교는 5장) |
| ③ 캐시 | 상세/목록 API 에 Redis look-aside 캐시 (TTL + 무효화 + fail-open) | 반복 조회의 DB 부하 절감. 캐시 미스·Redis 장애 시에도 정상 동작 |

**개선 순서가 곧 의존 순서다**: 캐시는 "없어도 되는 가속 레이어"(fail-open)라는 전제가 성립하려면 캐시 미스 시의 DB 쿼리가 인덱스로 이미 빨라야 한다. 그래서 ① 인덱스 → ② 정렬 구조 → ③ 캐시 순으로 진행했다.

### 측정 환경

- 로컬 docker MySQL 8.0 (`docker/infra-compose.yml`), Redis 7.0 master/replica
- 상품 100,000건 / 브랜드 200개 / product_like 표본 5,000건 시딩 (`LocalDataSeeder`, 고정 시드)
- 분포: 브랜드는 멱집중(일부 브랜드에 상품 집중), like_count 는 Zipf 유사 멱분포(소수 상품에 좋아요 집중), 1.5% soft delete
- 전후 비교는 **동일 데이터셋**에서 수행 — local 은 `ddl-auto: create` 라 재기동 시 데이터가 초기화되므로, 인덱스 없는 상태로 기동·시딩 후 같은 세션에서 `ALTER TABLE ... ADD INDEX` 로 After 를 측정하고, 측정 후 `@Table(indexes = ...)` 애너테이션으로 영구화했다.

---

## 2. 개선 ① — 목록 조회 인덱스 최적화

### 유즈케이스와 쿼리

| 유즈케이스 | 쿼리 형태 |
| --- | --- |
| Q1. 브랜드 필터 + 좋아요순 | `WHERE deleted_at IS NULL AND brand_id = ? ORDER BY like_count DESC, id DESC LIMIT 20` |
| Q2. 전체 좋아요순 | `WHERE deleted_at IS NULL ORDER BY like_count DESC, id DESC LIMIT 20` |
| Q3. 깊은 페이지 (offset 5000) | Q2 + `OFFSET 5000` |
| Q4. 페이지 수 계산 count | `SELECT COUNT(*) WHERE deleted_at IS NULL AND brand_id = ?` |

정렬에 `id DESC` tie-breaker 를 포함한 이유: like_count 동점 상품이 많을 때(0 인 상품이 다수) 정렬이 비결정적이면 페이지 경계에서 중복/누락이 발생한다.

### 인덱스 설계

| 인덱스 | 컬럼 | 대상 |
| --- | --- | --- |
| `idx_product_brand_deleted_like` | `(brand_id, deleted_at, like_count)` | Q1, Q4 |
| `idx_product_deleted_like` | `(deleted_at, like_count)` | Q2, Q3 |

- **컬럼 순서**: 동등 조건(`brand_id =`, `deleted_at IS NULL` — MySQL 은 IS NULL 을 ref 접근으로 처리) → 정렬 컬럼(`like_count`). 이 구조여야 인덱스 순서를 그대로 소비해 filesort 없이 LIMIT 에서 끊는다.
- **DESC 인덱스를 만들지 않은 이유**: MySQL 8.0 은 ASC 인덱스를 backward index scan 으로 역방향 소비할 수 있다. 실측에서 `Index lookup ... (reverse)` 로 확인됐다. tie-breaker `id` 는 보조 인덱스 리프에 PK 가 포함되므로 별도 컬럼 추가가 불필요하다.
- 최신순(latest) 정렬은 auto-increment PK 역순(`id DESC`)으로 처리되어 PK 인덱스로 충분하므로 별도 인덱스를 만들지 않았다.

### EXPLAIN / EXPLAIN ANALYZE 전후 비교 (실측)

| 쿼리 | Before | After | 개선 |
| --- | --- | --- | --- |
| Q1 브랜드+좋아요순 | `ALL` 풀스캔 100,000행 + filesort, **32.7ms** | `ref` 역방향 인덱스 룩업 20행, **0.16ms** | **~204x** |
| Q2 전체 좋아요순 | 풀스캔 + filesort, **37ms** | 역방향 인덱스 스캔 20행, **0.10ms** | **~370x** |
| Q3 offset 5000 | 풀스캔 + filesort 5,020행, **48ms** | 인덱스 스캔 5,020행, **6.98ms** | **~7x** |
| Q4 count | 풀스캔, **14.2ms** | **covering index** 룩업, **1.22ms** | **~12x** |

Before 실행 계획 (Q1):

```
-> Limit: 20 row(s) (actual time=32.7..32.7)
  -> Sort: like_count DESC, id DESC, limit 20  ← filesort
    -> Filter: brand_id=1 AND deleted_at IS NULL (rows=6917)
      -> Table scan on product (rows=100000)   ← 풀스캔
```

After 실행 계획 (Q1):

```
-> Limit: 20 row(s) (actual time=0.153..0.158)
  -> Index lookup on product using idx_product_brand_deleted_like
     (brand_id=1, deleted_at=NULL) (reverse)   ← filesort 없음, 20행만 소비
```

### API 응답시간 (curl, 비캐시 page=5 기준)

| | Before | After |
| --- | --- | --- |
| 목록 API (브랜드+좋아요순) | 46~58ms | 20~32ms |

관찰: Q3 처럼 **깊은 offset 은 인덱스로도 앞행들을 건너뛰며 스캔**해야 해 개선 폭이 작다(7x). 근본 해결은 커서 기반(no-offset) 페이지네이션이며 6장 향후 개선으로 미룬다. count 쿼리는 covering index 로 12x 개선됐지만 필터 범위 전체 스캔이라는 본질은 남는다 — 트래픽이 더 커지면 count 없는 `Slice` 응답 전환을 검토한다.

---

## 3. 개선 ② — 좋아요 수 정렬 구조 (비정규화 like_count)

### 선택: 비정규화 컬럼 (vs Materialized View 는 5장 비교)

`ORDER BY 좋아요수` 를 인덱스로 처리하려면 정렬 기준이 상품 테이블의 물리 컬럼이어야 한다. `product_like` 를 실시간 COUNT/조인하는 구조는 정렬 시 전 상품 집계가 필요해 인덱스 최적화 자체가 불가능하다. `product.like_count` 비정규화로 정렬 문제를 ①의 인덱스 문제로 환원시켰다.

### 동기화 설계 — 정합성이 1순위

like_count 는 한 번 어긋나면 재집계 없이는 복구되지 않고, 정렬 기준이므로 오염이 곧 조회 결과 오염이다. 세 가지 장치로 방어했다.

1. **중복 방지의 최종 방어선 = DB 유니크 제약** `uk_product_like_user_product (user_id, product_id)`
2. **등록**: `INSERT IGNORE` 가 **1행을 실제 삽입했을 때만** increment. 예외 기반 처리(트랜잭션 rollback-only 오염) 없이 "등록과 증가"를 단일 문장 단위로 일치시킨다. 중복 등록은 no-op 성공(멱등).
3. **취소**: `DELETE` 의 **영향 행수가 1일 때만** decrement — 동시 취소의 이중 감소를 구조적으로 차단. 감소 쿼리는 `CASE WHEN like_count > 0` 으로 음수 방어.

증감은 JPA dirty checking(read-modify-write) 대신 **단일 UPDATE 문**을 사용한다:

```sql
UPDATE product SET like_count = like_count + 1 WHERE id = ?
```

| 대안 | 배제 이유 |
| --- | --- |
| JPA dirty checking | 동시 요청에서 lost update (읽은 시점 값 + 1 로 덮어씀) |
| 비관적 락 (SELECT FOR UPDATE) | 락 보유 시간이 트랜잭션 전체로 늘어남 |
| 낙관적 락 (@Version) | 좋아요처럼 충돌 잦은 카운터는 재시도 폭주 |
| **단일 UPDATE (채택)** | DB 가 행 단위로 직렬화 — 최단 락, 무손실 |

검증: 통합 테스트에서 **100명 동시 좋아요 → like_count == 100**, 동일 사용자 50회 동시 중복 등록 → 1, 50회 동시 취소 → 정확히 1 감소(음수 없음) 을 확인했다 (`ProductLikeServiceIntegrationTest`).

### 남은 한계 — hot row

인기 상품 한 행에 쓰기가 몰리면 행 락 직렬화로 좋아요 API 지연이 발생한다(조회가 아니라 **쓰기 경로가 먼저 병목**이 된다). 현재 규모에선 문제없으며, 대응(카운트 샤딩/버퍼링)은 6장 향후 개선으로 미룬다.

---

## 4. 개선 ③ — Redis 캐시

### 구현 방식: RedisTemplate 직접 사용 (look-aside)

| | RedisTemplate 직접 (채택) | @Cacheable + RedisCacheManager |
| --- | --- | --- |
| fail-open 제어 | try-catch 로 명시적 | CacheErrorHandler 로 가능하나 흐름이 프레임워크 뒤에 숨음 |
| modules/redis 수정 | 불필요 (기존 템플릿 2개 그대로) | CacheManager 빈 추가 필요 |
| master/replica 활용 | 읽기=REPLICA_PREFERRED, 무효화=MASTER 분리 가능 | 불가 |
| 코드량 | 다소 많음 | 적음 |

`modules/*` 에 도메인 코드를 두지 않는 제약과 fail-open 의 명시성을 우선해 RedisTemplate 직접 사용을 채택했다. 캐시 저장소(`RedisCacheRepository`)는 인프라 계층에 제네릭으로 두고, 키/TTL/캐시 대상 정책(`ProductCachePolicy`)은 application 계층에 분리했다.

### 캐시 키 / TTL 설계

| 대상 | 키 | TTL | 근거 |
| --- | --- | --- | --- |
| 상세 | `product:v1:detail:{productId}` | 10분 ± 10% 지터 | 변경 시 즉시 무효화하므로 길게. 지터로 동시 만료 스파이크(stampede) 완화 |
| 목록 | `product:v1:list:{brandId\|all}:{sort}:{page}:{size}` | 60초, **page < 5 만 캐시** | 키 조합이 많아 적중률이 원래 낮은 캐시 — 조회가 집중되는 앞쪽 페이지만 캐시해 키 공간을 줄이고, like_count 변동은 짧은 TTL 로 자연 수렴 |

키의 `v1` 세그먼트는 응답 스키마 변경 시 일괄 무효화 수단이다.

### 무효화 매트릭스

| 이벤트 | 상세 캐시 | 목록 캐시 |
| --- | --- | --- |
| 상품 수정/삭제 | **DEL** | TTL 위임 (60초 불일치 허용) |
| 좋아요 등록/취소 | **DEL** (좋아요 수 정합) | TTL 위임 (순위 변동은 60초 내 수렴) |

- 목록을 DEL 하지 않는 이유: 브랜드×정렬×페이지 키 전체 삭제는 SCAN 비용 대비 이득이 없고, 60초 불일치는 목록 화면에서 허용 가능한 수준으로 판단.
- **무효화는 DB 커밋 이후 실행** — Facade(트랜잭션 경계 밖)에서 서비스 호출 후 evict 하여, 롤백됐는데 캐시만 지워지는(또는 그 역) 순서 역전을 방지한다.
- 무효화는 master 템플릿으로 수행해 복제 지연의 영향을 받지 않는다.

### fail-open

Redis 예외는 전부 흡수한다: 조회 실패 = 캐시 미스로 DB 진행, 저장/무효화 실패 = 무시(TTL 로 수렴). 단위 테스트로 Redis 다운 상황에서 예외가 전파되지 않음을 검증했고, 통합 테스트로 미스→DB→적재, 히트, 무효화 동작을 검증했다.

### 실측 (curl)

| | 응답시간 |
| --- | --- |
| 상세 캐시 미스 | 34.8ms |
| 상세 캐시 히트 | **2.2~2.9ms (~13x)** |
| 목록 캐시 히트 | 3.3~4.1ms |

### 남은 리스크

- **적중률 30% 이하로 떨어지면**: 캐시 조회 지연 + 직렬화 비용만 추가되어 P99 가 오히려 악화될 수 있다. 앞쪽 페이지 한정 캐시가 1차 방어이고, 적중률 모니터링 후 대상 축소/확대를 조정한다.
- **stampede**: 동일 키 동시 미스 시 같은 쿼리가 중복 실행된다. 상세는 지터로 완화했고, 미스 쿼리 자체가 인덱스로 빨라(수 ms) 현재 규모에선 허용. 분산 락/논리적 만료는 향후 개선.

---

## 5. 비정규화 vs Materialized View(집계 테이블) 트레이드오프

MySQL 은 네이티브 MV 가 없어, MV 선택 시 `product_like_count` 집계 테이블 + 주기 배치/트리거 갱신으로 구현하게 된다.

| 기준 | 비정규화 like_count (채택) | MV / 집계 테이블 |
| --- | --- | --- |
| 실시간성 | 트랜잭션 내 동기 갱신 — 즉시 반영 | 갱신 주기만큼 지연 (배치) 또는 트리거 복잡도 |
| 조회 경로 | 단일 테이블 — `(brand_id, deleted_at, like_count)` 인덱스로 정렬 직결 | 조인 필요 — 정렬 인덱스를 집계 테이블 쪽에 둬야 하고 브랜드 필터와 결합이 복잡 |
| 쓰기 비용 | 좋아요마다 product 행 UPDATE (hot row 리스크) | 원본 쓰기와 분리 — 쓰기 경합은 낮음 |
| 정합성 복구 | 어긋나면 재집계 필요 (단, 증감 경로를 단일화해 예방) | 주기 재집계가 구조에 내장 — 자가 복구적 |
| 운영 복잡도 | 낮음 — 코드 경로 하나 | 배치 잡/트리거 + 모니터링 추가 |

**판단**: 현재 규모(10만 건, 단일 인스턴스)에서는 실시간성과 조회 경로 단순성이 주는 이득이 크고, 쓰기 경합은 아직 병목이 아니다. 좋아요 쓰기 TPS 가 hot row 병목을 만들기 시작하면 그때 집계 테이블(또는 카운트 샤딩)로 전환하는 것이 순서다 — 전환 시에도 조회 계약(`likeCount` 필드)은 유지되므로 마이그레이션 비용은 갱신 경로에 국한된다.

**시딩 데이터의 의도된 불일치**: like_count 는 분포로 생성했고 product_like 는 표본 5,000건만 시딩했다. 완전 정합에는 수천만 행이 필요해 성능 측정 목적상 생략했다. 운영에서 재집계 검증 도구를 만든다면 이 시딩 데이터는 전부 불일치로 판정되는 것이 정상이다.

---

## 6. 종합 결과 및 향후 개선

### 체크리스트 대비 결과

| 체크리스트 | 구현 | 검증 |
| --- | --- | --- |
| 🔖 brandId 검색 + 좋아요순 정렬 | QueryDSL 목록 API | E2E 테스트 + EXPLAIN |
| 🔖 유즈케이스별 인덱스 + 전후 비교 | 복합 인덱스 2개 | 2장 실측 표 (최대 370x) |
| ❤️ 목록/상세 좋아요 수 노출·정렬 | like_count 비정규화 | E2E 테스트 |
| ❤️ 등록/취소 동기화 | 원자적 UPDATE + 유니크 제약 + 영향행수 가드 | 동시성 통합 테스트 (100명 동시) |
| ⚡ Redis 캐시 + TTL/무효화 | look-aside + 커밋 후 DEL | 캐시 통합 테스트 + 실측 13x |
| ⚡ 캐시 미스에도 정상 동작 | fail-open (예외 전부 흡수) | fail-open 단위 테스트 |

### 향후 개선 (우선순위 순)

1. **커서 기반 페이지네이션** — 깊은 offset 의 근본 해결 (현재 7x 개선에 그침)
2. **count 없는 Slice 응답** — count 쿼리는 covering index 로도 필터 범위 전체 스캔
3. **hot row 대응** — 인기 상품 좋아요 쓰기 직렬화 시 카운트 샤딩 or 버퍼링(배치 flush)
4. **stampede 방지** — 적중률/미스 폭주 모니터링 후 분산 락 or 논리적 만료 도입
5. **재집계 검증 배치** — product_like ↔ like_count 정합성 감사 도구

### 재현 절차

```bash
docker-compose -f docker/infra-compose.yml up -d
./gradlew :apps:commerce-api:bootRun --args='--loopers.seed.enabled=true'   # 10만 건 시딩
# 측정 세션 동안 앱 재기동 금지 (local 은 ddl-auto=create)
docker exec -it docker-mysql-1 mysql -uapplication -papplication loopers
# EXPLAIN ANALYZE 로 Before 측정 → ALTER TABLE ... ADD INDEX → 동일 쿼리 After 측정
# API 검증: http/commerce-api/product-v1.http, like-v1.http
```
