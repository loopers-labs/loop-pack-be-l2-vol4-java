# ADR-035: CQRS 도입 범위 및 시점 결정

- 날짜: 2026-06-18
- 상태: 승인됨
- 관련: [ADR-019](./019-product-list-n-plus-one.md), [ADR-027](./027-applicationservice-vs-facade.md)

## 결정

현재 시점에서 **완전한 CQRS(Command/Query Service 분리)를 도입하지 않는다.**  
단, 조회 최적화를 위한 `ProductQueryRepository` 는 유지하며, 이를 **CQRS-lite** 로 정의한다.  
캐시 도입 시에도 `ProductApplicationService` 단일 서비스 구조를 유지하되, AOP(`@Cacheable` / `@CacheEvict`)로 캐시 관심사를 분리한다.

---

## 배경

상품 목록 조회(N+1 문제) 해결 과정에서 두 가지 조회 방식을 성능 테스트로 비교했다.

### 배치 조회 방식 (IN 쿼리 3회)

```
1. SELECT * FROM product WHERE ... ORDER BY price DESC LIMIT 20
2. SELECT * FROM brand WHERE id IN (...)
3. SELECT * FROM inventory WHERE product_id IN (...)
```

### QueryDSL JOIN 방식 (2회)

```
1. SELECT p.*, b.name, i.quantity FROM product p JOIN brand b JOIN inventory i WHERE ... LIMIT 20
2. SELECT COUNT(*) FROM product WHERE ...
```

### 성능 테스트 결과 요약 (100만 건 기준, page=0, size=20)

| 방식 | brandId 없음 avg | brandId=15 avg |
|---|---|---|
| 배치 조회 | ~107ms | ~5ms |
| JOIN (인덱스 최적화 후) | ~86ms | ~3ms |

인덱스 추가(`deleted_at`, `price`, `like_count`, `brand_id` 복합) 및 COUNT 쿼리 JOIN 제거 후 JOIN 방식이 소폭 우위를 보였다.

---

## CQRS 도입 검토 결과

### 현재 구조 (CQRS-lite)

```
ProductApplicationService     ← Command + Query 혼재
ProductQueryRepository        ← 조회 전용 쿼리 구현체 (분리 완료)
```

### 완전한 CQRS 구조

```
ProductCommandService         ← 상태 변경 전담
ProductQueryService           ← 조회 전담
ProductQueryRepository        ← 조회 최적화 쿼리
```

### 완전한 CQRS를 도입하지 않는 이유

| 항목 | 판단 |
|---|---|
| Read/Write DB 분리 여부 | 단일 MySQL — CQRS 핵심 이점 미실현 |
| 조회 성능 | ~86ms — 현재 허용 범위 |
| 구조 복잡도 대비 효과 | 파일 증가, 흐름 파악 비용 대비 이득 미미 |
| `ProductQueryRepository` 로 부분 분리 | 이미 조회 책임 일부 격리됨 |

---

## Redis 캐시 도입 시 설계 방향

캐시 도입 시에도 ApplicationService 분리 없이 AOP로 캐시 관심사를 위임한다.

```java
@CacheEvict(value = "products", allEntries = true)
public void updateProduct(...) { /* 도메인 로직만 */ }

@Cacheable(value = "products", key = "#brandId + ':' + #pageable")
public Page<ProductInfo> getAllProducts(...) { /* 조회 로직만 */ }
```

Cache Stampede 방어(Single Flight, Jitter 등)는 `CacheManager` 레벨에서 처리하며, 이는 서비스 분리 여부와 무관하게 적용 가능하다.

---

## CQRS 완전 도입을 재검토하는 시점

아래 조건 중 하나 이상이 충족될 때 Command/Query Service 분리를 재검토한다.

1. **Read Replica 도입** — DataSource 자체가 분리되어 서비스 분리가 자연스러워질 때
2. **이벤트 기반 캐시 무효화** — Command가 Event를 발행하고 Query가 구독하는 구조가 필요할 때
3. **조회/쓰기 트랜잭션 설정 분리** — `readOnly=true` DataSource 분리 등

---

## 조회 방식 최종 결정

성능과 아키텍처 원칙을 함께 고려하여 **배치 조회 방식을 기본**으로 채택한다.

| 관점 | 배치 조회 | JOIN |
|---|---|---|
| 도메인 경계 | 유지 | 침범 (cross-domain JOIN) |
| 옵티마이저 예측성 | 높음 (단순 쿼리) | 낮음 (실행 계획 가변) |
| 데이터 정합성 | 안전 | COUNT/content 불일치 가능성 |
| 성능 차이 | ~107ms | ~86ms |
| 쿼리 수 | 4회 | 2회 |

20ms 차이는 캐시로 해소 가능한 범위이며, JOIN 방식은 옵티마이저 동작 변화에 취약하다는 점을 실험으로 확인했다. (`brand.deleted_at IS NULL` 조건 하나로 드라이빙 테이블이 바뀌어 2,000ms로 급등한 사례)
